package model;

public record OwnershipHistory(int ownershipId, String ownerName, String startDate, String endDate,
                               String transferType, String deedNo, Double consideration, String remarks) { }
