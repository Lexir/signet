/**
 * Разделяемое ядро (shared kernel): доменные сущности, репозитории и события,
 * которыми обмениваются модули. Помечено как OPEN, поэтому остальные модули
 * могут обращаться к его типам напрямую.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.signet.shared;
