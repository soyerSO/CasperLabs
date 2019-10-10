#![no_std]

extern crate contract_ffi;

use contract_ffi::contract_api::account;

#[no_mangle]
pub extern "C" fn call() {
    loop {
        let _main_purse = account::get_main_purse();
    }
}
