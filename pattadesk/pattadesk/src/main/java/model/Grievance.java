package model;

public record Grievance(int grievanceId, String ticketId, int parcelId, String surveyNo, String village,
                        String category, String subject, String description, String status, String priority,
                        int citizenId, String citizenName, String citizenEmail, String citizenPhone,
                        Integer assignedTo, String adminName, String adminRemarks,
                        String createdAt, String assignedAt, String resolvedAt) { }
