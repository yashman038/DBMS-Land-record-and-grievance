package model;

/** The logged-in user (also stored in the HTTP session). Aadhaar is always masked. */
public record User(int userId, String name, String email, String aadhaarMasked,
                   String phone, String role, String createdAt) { }
