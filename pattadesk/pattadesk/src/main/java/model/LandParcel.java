package model;

public record LandParcel(int parcelId, String surveyNo, String district, String taluk, String village,
                         double areaAcres, String landType, String landUse,
                         Double latitude, Double longitude, Double marketValue,
                         String status, String encumbrance,
                         int ownerId, String ownerName, String createdAt) { }
