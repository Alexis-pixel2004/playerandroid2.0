package com.alexis.hifiplayer.domain.model

/**
 * Preferencias de formato de salida de audio (Fase 3.1).
 *
 * Describe qué *prefiere* el usuario para la salida; **no garantiza** que el
 * hardware o el mixer del sistema cumplan la preferencia. Cada campo es una
 * pista para el motor de reproducción, no un requisito estricto.
 *
 * Convención conservadora: `false`/`null` por defecto = no activar funciones
 * que puedan alterar la señal sin consentimiento explícito del usuario.
 */
data class OutputFormatPreference(
    /**
     * Solicitar PCM de alta resolución (>16 bits: 24/32 bits o punto flotante)
     * cuando el decodificador y el dispositivo lo permitan.
     *
     * `false` por defecto: el motor usará 16 bits a menos que el usuario lo
     * habilite expresamente. No implica bit-perfect.
     */
    val preferHighResPcm: Boolean = false,

    /**
     * Preferir un dispositivo USB DAC detectado como salida.
     *
     * `false` por defecto: el sistema elegirá la ruta habitual. Si se activa
     * y hay un USB DAC disponible, se pasa como preferencia al motor; **no
     * garantiza** que Android la respete.
     */
    val preferUsbDac: Boolean = false,

    /**
     * Permitir reproducción *offload* (decodificación en DSP del codec/dispositivo).
     *
     * `false` por defecto: decodificación en CPU (software). El offload puede
     * ahorrar batería pero **puede** romper gapless, cambio de velocidad u otras
     * funciones; también **no implica** bit-perfect.
     */
    val allowOffload: Boolean = false
) {
    /**
     * Devuelve `true` si alguna preferencia no conservadora está activa.
     * Útil para logging o diagnósticos.
     */
    val hasNonDefaultPreferences: Boolean
        get() = preferHighResPcm || preferUsbDac || allowOffload
}