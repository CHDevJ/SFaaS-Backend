package com.sfaasproject.stf.operations.port;

import java.math.BigDecimal;

/** A settings query. B does not read A repositories or update settings. */
public interface SettingsQuery {
    int integer(String key, int fallback);

    BigDecimal decimal(String key, BigDecimal fallback);
}
