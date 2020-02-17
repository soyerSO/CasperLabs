//! Functions for accessing and mutating local and global state.

use alloc::{collections::BTreeMap, string::String, vec::Vec};
use core::{convert::From, mem::MaybeUninit};

use casperlabs_types::{
    api_error,
    bytesrepr::{self, FromBytes, ToBytes},
    AccessRights, ApiError, CLTyped, CLValue, ContractRef, Key, URef,
};

use crate::{
    contract_api::{self, runtime, TURef},
    ext_ffi,
    unwrap_or_revert::UnwrapOrRevert,
};

/// Reads value under `turef` in the global state.
pub fn read<T: CLTyped + FromBytes>(turef: TURef<T>) -> Result<Option<T>, bytesrepr::Error> {
    let key: Key = turef.into();
    let (key_ptr, key_size, _bytes) = contract_api::to_ptr(key);

    let value_size = {
        let mut value_size = MaybeUninit::uninit();
        let ret = unsafe { ext_ffi::read_value(key_ptr, key_size, value_size.as_mut_ptr()) };
        match api_error::result_from(ret) {
            Ok(_) => unsafe { value_size.assume_init() },
            Err(ApiError::ValueNotFound) => return Ok(None),
            Err(e) => runtime::revert(e),
        }
    };

    let value_bytes = runtime::read_host_buffer(value_size).unwrap_or_revert();
    Ok(Some(bytesrepr::deserialize(value_bytes)?))
}

/// Reads the value under `key` in the context-local partition of global state.
pub fn read_local<K: ToBytes, V: CLTyped + FromBytes>(
    key: &K,
) -> Result<Option<V>, bytesrepr::Error> {
    let key_bytes = key.to_bytes()?;

    let value_size = {
        let mut value_size = MaybeUninit::uninit();
        let ret = unsafe {
            ext_ffi::read_value_local(key_bytes.as_ptr(), key_bytes.len(), value_size.as_mut_ptr())
        };
        match api_error::result_from(ret) {
            Ok(_) => unsafe { value_size.assume_init() },
            Err(ApiError::ValueNotFound) => return Ok(None),
            Err(e) => runtime::revert(e),
        }
    };

    let value_bytes = runtime::read_host_buffer(value_size).unwrap_or_revert();
    Ok(Some(bytesrepr::deserialize(value_bytes)?))
}

/// Writes `value` under `turef` in the global state.
pub fn write<T: CLTyped + ToBytes>(turef: TURef<T>, value: T) {
    let key = Key::from(turef);
    let (key_ptr, key_size, _bytes1) = contract_api::to_ptr(key);

    let cl_value = CLValue::from_t(value).unwrap_or_revert();
    let (cl_value_ptr, cl_value_size, _bytes2) = contract_api::to_ptr(cl_value);

    unsafe {
        ext_ffi::write(key_ptr, key_size, cl_value_ptr, cl_value_size);
    }
}

/// Writes `value` under `key` in the context-local partition of global state.
pub fn write_local<K: ToBytes, V: CLTyped + ToBytes>(key: K, value: V) {
    let (key_ptr, key_size, _bytes1) = contract_api::to_ptr(key);

    let cl_value = CLValue::from_t(value).unwrap_or_revert();
    let (cl_value_ptr, cl_value_size, _bytes) = contract_api::to_ptr(cl_value);

    unsafe {
        ext_ffi::write_local(key_ptr, key_size, cl_value_ptr, cl_value_size);
    }
}

/// Adds `value` to the one currently under `turef` in the global state.
pub fn add<T: CLTyped + ToBytes>(turef: TURef<T>, value: T) {
    let key = Key::from(turef);
    let (key_ptr, key_size, _bytes1) = contract_api::to_ptr(key);

    let cl_value = CLValue::from_t(value).unwrap_or_revert();
    let (cl_value_ptr, cl_value_size, _bytes2) = contract_api::to_ptr(cl_value);

    unsafe {
        // Could panic if `value` cannot be added to the given value in memory.
        ext_ffi::add(key_ptr, key_size, cl_value_ptr, cl_value_size);
    }
}

/// Adds `value` to the one currently under `key` in the context-local partition of global state.
pub fn add_local<K: ToBytes, V: CLTyped + ToBytes>(key: K, value: V) {
    let (key_ptr, key_size, _bytes1) = contract_api::to_ptr(key);

    let cl_value = CLValue::from_t(value).unwrap_or_revert();
    let (cl_value_ptr, cl_value_size, _bytes) = contract_api::to_ptr(cl_value);

    unsafe {
        ext_ffi::add_local(key_ptr, key_size, cl_value_ptr, cl_value_size);
    }
}

/// Stores the serialized bytes of an exported, non-mangled `extern "C"` function as a new contract
/// under a [`URef`] generated by the host.
pub fn store_function(name: &str, named_keys: BTreeMap<String, Key>) -> ContractRef {
    let (fn_ptr, fn_size, _bytes1) = contract_api::to_ptr(name);
    let (keys_ptr, keys_size, _bytes2) = contract_api::to_ptr(named_keys);
    let mut addr = [0u8; 32];
    unsafe {
        ext_ffi::store_function(fn_ptr, fn_size, keys_ptr, keys_size, addr.as_mut_ptr());
    }
    ContractRef::URef(URef::new(addr, AccessRights::READ_ADD_WRITE))
}

/// Stores the serialized bytes of an exported, non-mangled `extern "C"` function as a new contract
/// at an immutable address generated by the host.
pub fn store_function_at_hash(name: &str, named_keys: BTreeMap<String, Key>) -> ContractRef {
    let (fn_ptr, fn_size, _bytes1) = contract_api::to_ptr(name);
    let (keys_ptr, keys_size, _bytes2) = contract_api::to_ptr(named_keys);
    let mut addr = [0u8; 32];
    unsafe {
        ext_ffi::store_function_at_hash(fn_ptr, fn_size, keys_ptr, keys_size, addr.as_mut_ptr());
    }
    ContractRef::Hash(addr)
}

/// Returns a new unforgeable pointer, where the value is initialized to `init`.
pub fn new_turef<T: CLTyped + ToBytes>(init: T) -> TURef<T> {
    let key_ptr = contract_api::alloc_bytes(Key::serialized_size_hint());
    let cl_value = CLValue::from_t(init).unwrap_or_revert();
    let (cl_value_ptr, cl_value_size, _cl_value_bytes) = contract_api::to_ptr(cl_value);
    let bytes = unsafe {
        ext_ffi::new_uref(key_ptr, cl_value_ptr, cl_value_size); // URef has `READ_ADD_WRITE` access
        Vec::from_raw_parts(
            key_ptr,
            Key::serialized_size_hint(),
            Key::serialized_size_hint(),
        )
    };
    let key: Key = bytesrepr::deserialize(bytes).unwrap_or_revert();
    if let Key::URef(uref) = key {
        TURef::from_uref(uref).unwrap_or_revert()
    } else {
        runtime::revert(ApiError::UnexpectedKeyVariant);
    }
}