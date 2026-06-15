use async_trait::async_trait;
use futures::channel::oneshot::{channel, Sender};
use jni::{
    objects::{GlobalRef, JObject, JObjectArray, JValue},
    sys::jlong,
    JNIEnv, JavaVM,
};
use juicebox_sdk as sdk;
use rand_core::{OsRng, RngCore};
use std::collections::HashMap;
use std::sync::Mutex;

use crate::{
    jni_array, jni_object, jni_signature,
    types::{
        JNI_BYTE_TYPE, JNI_LONG_TYPE, JNI_STRING_TYPE, JNI_VOID_TYPE,
        JUICEBOX_JNI_HTTP_HEADER_TYPE, JUICEBOX_JNI_HTTP_REQUEST_TYPE,
    },
};

pub struct HttpClient {
    send_function: GlobalRef,
    jvm: JavaVM,
    request_map: Mutex<HashMap<[u8; 16], Sender<Option<sdk::http::Response>>>>,
}

impl HttpClient {
    pub fn new(send_function: GlobalRef, jvm: JavaVM) -> Self {
        HttpClient {
            send_function,
            jvm,
            request_map: Mutex::new(HashMap::new()),
        }
    }

    fn remove(&self, request_id: &[u8; 16]) {
        let tx = {
            let mut locked = self.request_map.lock().unwrap();
            locked.remove(request_id)
        };
        drop(tx);
    }

    pub fn receive(&self, response_id: [u8; 16], response: Option<sdk::http::Response>) {
        let tx = {
            let mut locked = self.request_map.lock().unwrap();
            locked.remove(&response_id)
        };
        if let Some(tx) = tx {
            let _ = tx.send(response);
        }
    }
}

#[async_trait]
impl sdk::http::Client for HttpClient {
    async fn send(&self, request: sdk::http::Request) -> Option<sdk::http::Response> {
        let (tx, rx) = channel();
        let mut id = [0u8; 16];
        OsRng.fill_bytes(&mut id);

        {
            let mut env = match self.jvm.attach_current_thread() {
                Ok(env) => env,
                Err(_) => return None,
            };

            let java_request_class = match env.find_class(JUICEBOX_JNI_HTTP_REQUEST_TYPE) {
                Ok(value) => value,
                Err(_) => {
                    clear_pending_exception(&mut env);
                    return None;
                }
            };
            let java_request = match env.new_object(
                java_request_class,
                jni_signature!(() => JNI_VOID_TYPE),
                &[],
            ) {
                Ok(value) => value,
                Err(_) => {
                    clear_pending_exception(&mut env);
                    return None;
                }
            };

            if set_byte_array(&mut env, &java_request, "id", &id).is_err() {
                clear_pending_exception(&mut env);
                return None;
            }

            if set_string(&mut env, &java_request, "method", request.method.as_str()).is_err() {
                clear_pending_exception(&mut env);
                return None;
            }

            if set_string(&mut env, &java_request, "url", request.url.as_str()).is_err() {
                clear_pending_exception(&mut env);
                return None;
            }

            if let Some(body) = request.body {
                if set_byte_array(&mut env, &java_request, "body", &body).is_err() {
                    clear_pending_exception(&mut env);
                    return None;
                }
            }

            let mut headers_array: Option<JObjectArray> = None;

            if !request.headers.is_empty() {
                let java_header_class = match env.find_class(JUICEBOX_JNI_HTTP_HEADER_TYPE) {
                    Ok(value) => value,
                    Err(_) => {
                        clear_pending_exception(&mut env);
                        return None;
                    }
                };

                for (index, (name, value)) in request.headers.iter().enumerate() {
                    let java_header = match env.new_object(
                        &java_header_class,
                        jni_signature!(() => JNI_VOID_TYPE),
                        &[],
                    ) {
                        Ok(value) => value,
                        Err(_) => {
                            clear_pending_exception(&mut env);
                            return None;
                        }
                    };

                    if set_string(&mut env, &java_header, "name", name).is_err() {
                        clear_pending_exception(&mut env);
                        return None;
                    }

                    if set_string(&mut env, &java_header, "value", value).is_err() {
                        clear_pending_exception(&mut env);
                        return None;
                    }

                    match &headers_array {
                        Some(array) => {
                            if env
                                .set_object_array_element(
                                    array,
                                    index.try_into().ok()?,
                                    java_header,
                                )
                                .is_err()
                            {
                                clear_pending_exception(&mut env);
                                return None;
                            }
                        }
                        None => {
                            headers_array = match env.new_object_array(
                                request.headers.len().try_into().ok()?,
                                JUICEBOX_JNI_HTTP_HEADER_TYPE,
                                java_header,
                            ) {
                                Ok(value) => Some(value),
                                Err(_) => {
                                    clear_pending_exception(&mut env);
                                    return None;
                                }
                            };
                        }
                    };
                }
            }

            if let Some(array) = headers_array {
                if env
                    .set_field(
                        &java_request,
                        "headers",
                        jni_array!(jni_object!(JUICEBOX_JNI_HTTP_HEADER_TYPE)),
                        JValue::Object(&array),
                    )
                    .is_err()
                {
                    clear_pending_exception(&mut env);
                    return None;
                }
            }

            {
                let mut request_map = self.request_map.lock().unwrap();
                request_map.insert(id, tx);
            }

            let send_result = env.call_method(
                &self.send_function,
                "send",
                jni_signature!((JNI_LONG_TYPE, jni_object!(JUICEBOX_JNI_HTTP_REQUEST_TYPE)) => JNI_VOID_TYPE),
                &[
                    (self as *const HttpClient as jlong).into(),
                    JValue::Object(&java_request),
                ],
            );

            if send_result.is_err() {
                clear_pending_exception(&mut env);
                self.remove(&id);
                return None;
            }
        }

        rx.await.unwrap_or(None)
    }
}

fn set_string(
    env: &mut JNIEnv,
    obj: &JObject,
    name: &str,
    string: &str,
) -> jni::errors::Result<()> {
    let java_string = env.new_string(string)?;
    env.set_field(
        obj,
        name,
        jni_object!(JNI_STRING_TYPE),
        JValue::Object(&java_string),
    )?;
    Ok(())
}

fn set_byte_array(
    env: &mut JNIEnv,
    obj: &JObject,
    name: &str,
    array: &[u8],
) -> jni::errors::Result<()> {
    let java_array = env.byte_array_from_slice(array)?;
    env.set_field(
        obj,
        name,
        jni_array!(JNI_BYTE_TYPE),
        JValue::Object(&java_array),
    )?;
    Ok(())
}

fn clear_pending_exception(env: &mut JNIEnv) {
    if env.exception_check().unwrap_or(false) {
        let _ = env.exception_clear();
    }
}
