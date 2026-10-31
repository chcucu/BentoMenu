package dev.bento.menu.api.v1;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Convenience accessor for the registered {@link BentoApi} service.
 */
public final class BentoApiProvider {

    private BentoApiProvider() {
    }

    /**
     * @throws IllegalStateException if BentoMenu is not installed or not enabled
     */
    public static BentoApi get() {
        RegisteredServiceProvider<BentoApi> registration =
                Bukkit.getServicesManager().getRegistration(BentoApi.class);
        if (registration == null || registration.getProvider() == null) {
            throw new IllegalStateException("BentoMenu is not installed or not enabled");
        }
        return registration.getProvider();
    }
}
