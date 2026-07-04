use std::io::{self, Write};
use std::fs;
use std::path::Path;
use vaults_core::{generate_salt, save_salt, load_salt, derive_master_key, derive_pin, generate_totp_secret, verify_totp};

fn prompt(label: &str) -> String {
    print!("{}: ", label);
    io::stdout().flush().unwrap();
    let mut input = String::new();
    io::stdin().read_line(&mut input).unwrap();
    input.trim().to_string()
}

fn main() {
    let salt_path = "vaults.salt";
    let totp_path = "vaults.totp";

    let salt = match load_salt(salt_path) {
        Some(s) => {
            println!("Loaded existing salt");
            s
        }
        None => {
            println!("First run — generating salt");
            let s = generate_salt();
            save_salt(&s, salt_path);
            s
        }
    };

    let totp_secret = if Path::new(totp_path).exists() {
        fs::read_to_string(totp_path).unwrap().trim().to_string()
    } else {
        println!("First run — generating TOTP secret");
        let secret = generate_totp_secret();
        fs::write(totp_path, &secret).unwrap();
        println!("Your TOTP secret (add to authenticator app): {}", secret);
        secret
    };

    let code = prompt("Enter TOTP code");
    if !verify_totp(&totp_secret, &code) {
        println!("Invalid TOTP code. Access denied.");
        return;
    }

    println!("TOTP verified.");

    let passphrase = prompt("Master passphrase");
    let country = prompt("Country code (e.g. TZ)");
    let service = prompt("Service name (e.g. tigopesa)");
    let phone = prompt("Phone number (e.g. +255712345678)");
    let pin_length: u32 = prompt("PIN length (4, 5, or 6)").parse().unwrap_or(4);

    let context = format!("v1|{}|{}|{}|{}",
        country.to_lowercase(),
        service.to_lowercase(),
        phone,
        pin_length
    );

    let master_key = derive_master_key(&passphrase, &salt);
    let pin = derive_pin(&master_key, &context, pin_length);

    println!("\nYour {} PIN: {}", service, pin);
}