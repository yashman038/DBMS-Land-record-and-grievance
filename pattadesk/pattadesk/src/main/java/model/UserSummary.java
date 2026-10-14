package model;

/** One row of the admin "User Management" table. */
public record UserSummary(int userId, String name, String email, String phone, String role,
                          String createdAt, int parcelCount, int grievanceCount) { }
