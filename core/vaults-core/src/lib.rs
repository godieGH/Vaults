// src/lib.rs

uniffi::include_scaffolding!("vaults");

pub mod ffi;
pub use ffi::ffi_generate_salt;
pub use ffi::ffi_derive_master_key;
pub use ffi::ffi_derive_pin;
pub use ffi::ffi_generate_totp_secret;
pub use ffi::ffi_verify_totp;
pub use ffi::ffi_derive_passphrase_fingerprint;

use rand::RngCore;
use rand::rngs::OsRng;
use pbkdf2::pbkdf2_hmac;
use sha2::Sha256;
use hkdf::Hkdf;
use std::fs;
use std::path::Path;

// stage 1: generate a random salt and derive a master key

// this function generates a random salt of 32 bytes using the operating system's random number generator.
pub fn generate_salt() -> [u8; 32] {
    let mut salt = [0u8; 32];
    OsRng.fill_bytes(&mut salt);
    salt
}

// this function derives a master key from a given passphrase and salt using the PBKDF2 algorithm with HMAC-SHA256 as the underlying hash function. It performs 600,000 iterations to make brute-force attacks more difficult.
pub fn derive_master_key(passphrase: &str, salt: &[u8]) -> [u8; 32] {
    let mut master_key = [0u8; 32];
    pbkdf2_hmac::<Sha256>(
        passphrase.as_bytes(),
        salt,
        600_000,
        &mut master_key,
    );
    master_key
}

// stage 2: HKDF. This takes the master key and a service context string and produces the final raw bytes we'll turn into a PIN

// this function derives a service-specific key from the master key and a context string using the HKDF algorithm with HMAC-SHA256 as the underlying hash function. It produces a 16-byte service key.
pub fn derive_service_key(master_key: &[u8], context: &str) -> [u8; 16] {
    let hk = Hkdf::<Sha256>::new(None, master_key);
    let mut service_key = [0u8; 16];
    hk.expand(context.as_bytes(), &mut service_key)
        .expect("HKDF expand failed");
    service_key
}

// stage 3: derive the PIN from the service key. This takes the first 8 bytes of the service key, converts it to a u64, and then takes the modulus with 10^pin_length to get a number in the range [0, 10^pin_length). It then formats this number as a string with leading zeros to ensure it has the correct length.
// cotext format: "v1|<service_name>|<phone_number>|<pin_length>" v1 indicates the version of the context format ( If we ever change the algorithm in future, we just bump to v2 and old PINs don't collide with new ones), TZ is the country code for Tanzania, tigopesa is the service name, +255712345678 is the phone number, and 4 is the desired length of the PIN.
pub fn derive_pin(master_key: &[u8], context: &str, pin_length: u32) -> String {
    let service_key = derive_service_key(master_key, context);
    
    let modulus = 10u64.pow(pin_length);
    let raw = u64::from_be_bytes(service_key[0..8].try_into().unwrap());
    let pin_number = raw % modulus;
    
    format!("{:0>width$}", pin_number, width = pin_length as usize)
}



// extras prototyping: utility & helpers
pub fn save_salt(salt: &[u8; 32], path: &str) {
    fs::write(path, hex::encode(salt)).expect("Failed to save salt");
}

pub fn load_salt(path: &str) -> Option<[u8; 32]> {
    if !Path::new(path).exists() {
        return None;
    }
    let hex_str = fs::read_to_string(path).expect("Failed to read salt file");
    let bytes = hex::decode(hex_str.trim()).expect("Invalid salt format");
    let mut salt = [0u8; 32];
    salt.copy_from_slice(&bytes);
    Some(salt)
}


use totp_rs::{TOTP, Algorithm, Secret};

pub fn generate_totp_secret() -> String {
    Secret::generate_secret().to_encoded().to_string()
}

pub fn verify_totp(secret: &str, code: &str) -> bool {
    let totp = TOTP::new(
        Algorithm::SHA1,
        6,
        1,
        30,
        Secret::Encoded(secret.to_string()).to_bytes().unwrap(),
    ).unwrap();

    totp.check_current(code).unwrap_or(false)
}

pub fn derive_passphrase_fingerprint(passphrase: &str, salt: &[u8]) -> String {
    let master_key = derive_master_key(passphrase, salt);
    let hk = Hkdf::<Sha256>::new(None, &master_key);
    let mut fingerprint = [0u8; 8];
    hk.expand(b"v1|verify", &mut fingerprint).expect("HKDF expand failed");
    hex::encode(fingerprint)
}