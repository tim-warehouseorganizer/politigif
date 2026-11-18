/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.status;

/**
 * The no-delete rule (2026-11-18): NO data is ever deleted from the database and no stored MP4 is
 * ever deleted. Delete, wipe, hide, reset all mean flipping an integer {@code status} field on the
 * record, and every query filters by it. The one exception is a court order, handled by hand
 * outside the app ({@code docs/court-ordered-deletion.md}); {@code NoDeleteArchitectureTest} fails
 * the build if a delete call comes back.
 *
 * <p>Copied from warehouseorganizer: {@code util/sql/AuditableEntity.status}, typed
 * {@code util/sql/EntityStatusType { DELETED, ACTIVE }}, stored by JPA as the enum ORDINAL in a
 * {@code SMALLINT status} column (Liquibase {@code db/changelog/03-06-changelog.xml} onward), with
 * queries of the shape {@code findBy...AndStatus(EntityStatusType.ACTIVE)} and soft deletes of the
 * shape {@code setStatus(EntityStatusType.DELETED)} ({@code service/costs/CostService.softDelete}).
 * Same field name ({@code status}), same storage (an integer), same two first values
 * (0 = DELETED, 1 = ACTIVE). One difference on purpose: each politigif status enum carries an
 * explicit code instead of relying on ordinal position, so reordering an enum can never silently
 * change what stored rows mean. New states are appended after 1.
 */
public interface Status {

    /** WHO's {@code EntityStatusType.DELETED} ordinal. Reserved; nothing in politigif sets it. */
    int DELETED = 0;

    /** WHO's {@code EntityStatusType.ACTIVE} ordinal. */
    int ACTIVE = 1;

    /** The stored integer. */
    int code();

    /** The enum constant for a stored code. */
    static <E extends Enum<E> & Status> E of(Class<E> type, int code) {
        for (E e : type.getEnumConstants()) {
            if (e.code() == code) {
                return e;
            }
        }
        throw new IllegalArgumentException("unknown " + type.getSimpleName() + " code " + code);
    }
}
