package com.trainconcierge.service;

import com.trainconcierge.dto.TrainStatusResponse;

/**
 * Result of a create-or-update status operation: the saved status together with
 * a flag telling the controller whether a new row was created ({@code 201}) or an
 * existing one was updated ({@code 200}).
 */
public record StatusUpsertResult(TrainStatusResponse status, boolean created) {
}
