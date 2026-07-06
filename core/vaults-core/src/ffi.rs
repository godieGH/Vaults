use crate::{derive_master_key, derive_pin, generate_salt, generate_totp_secret, verify_totp, derive_passphrase_fingerprint};

pub fn ffi_generate_salt() -> Vec<u8> {
    generate_salt().to_vec()
}

pub fn ffi_derive_master_key(passphrase: String, salt: Vec<u8>) -> Vec<u8> {
    derive_master_key(&passphrase, &salt).to_vec()
}

pub fn ffi_derive_pin(master_key: Vec<u8>, context: String, pin_length: u32) -> String {
    derive_pin(&master_key, &context, pin_length)
}

pub fn ffi_generate_totp_secret() -> String {
    generate_totp_secret()
}

pub fn ffi_verify_totp(secret: String, code: String) -> bool {
    verify_totp(&secret, &code)
}

pub fn ffi_derive_passphrase_fingerprint(passphrase: String, salt: Vec<u8>) -> String {
    derive_passphrase_fingerprint(&passphrase, &salt)
}