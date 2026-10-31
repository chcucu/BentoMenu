package dev.bento.menu.bukkit.integration;

import dev.bento.menu.api.v1.service.TransactionResult;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Locale;

/**
 * Vault economy adapter. Vault is one of the two mandatory dependencies; an
 * economy provider registered through Vault is required for money features.
 */
public final class EconomyBridge implements dev.bento.menu.api.v1.service.EconomyService {

    private final org.bukkit.plugin.Plugin plugin;
    private final String fallbackFormat;
    private volatile Economy economy;
    private volatile long lastLookup;
    private volatile boolean warned;

    public EconomyBridge(org.bukkit.plugin.Plugin plugin, String fallbackFormat) {
        this.plugin = plugin;
        this.fallbackFormat = fallbackFormat;
        resolve();
        if (economy == null) {
            plugin.getLogger().info("No Vault economy provider is available yet; "
                    + "money features will resolve it lazily after all plugins enable.");
        }
    }

    @Override
    public boolean available() {
        return resolve() != null;
    }

    private Economy resolve() {
        Economy current = economy;
        if (current != null) {
            return current;
        }
        long now = System.nanoTime();
        if (now - lastLookup < 2_000_000_000L) {
            return null;
        }
        synchronized (this) {
            if (economy != null) {
                return economy;
            }
            if (now - lastLookup < 2_000_000_000L) {
                return null;
            }
            lastLookup = now;
            RegisteredServiceProvider<Economy> registration =
                    Bukkit.getServicesManager().getRegistration(Economy.class);
            if (registration != null && registration.getProvider() != null) {
                economy = registration.getProvider();
                plugin.getLogger().info("Vault economy provider detected: " + economy.getName());
            }
            return economy;
        }
    }

    @Override
    public double balance(Player player) {
        Economy current = resolve();
        if (current == null) {
            return 0;
        }
        try {
            return current.getBalance(player);
        } catch (RuntimeException exception) {
            warn(exception);
            return 0;
        }
    }

    @Override
    public String format(double amount) {
        Economy current = resolve();
        if (current == null) {
            return String.format(Locale.ROOT, fallbackFormat, amount);
        }
        try {
            return current.format(amount);
        } catch (RuntimeException exception) {
            warn(exception);
            return String.format(Locale.ROOT, fallbackFormat, amount);
        }
    }

    @Override
    public TransactionResult withdraw(Player player, double amount) {
        Economy current = resolve();
        if (current == null) {
            return TransactionResult.failure(amount, "economy-unavailable");
        }
        try {
            EconomyResponse response = current.withdrawPlayer(player, amount);
            return response.transactionSuccess()
                    ? TransactionResult.success(amount)
                    : TransactionResult.failure(amount, response.errorMessage);
        } catch (RuntimeException exception) {
            warn(exception);
            return TransactionResult.failure(amount, exception.getMessage());
        }
    }

    @Override
    public TransactionResult deposit(Player player, double amount) {
        Economy current = resolve();
        if (current == null) {
            return TransactionResult.failure(amount, "economy-unavailable");
        }
        try {
            EconomyResponse response = current.depositPlayer(player, amount);
            return response.transactionSuccess()
                    ? TransactionResult.success(amount)
                    : TransactionResult.failure(amount, response.errorMessage);
        } catch (RuntimeException exception) {
            warn(exception);
            return TransactionResult.failure(amount, exception.getMessage());
        }
    }

    public String providerName() {
        Economy current = resolve();
        return current == null ? "none" : current.getName();
    }

    private void warn(RuntimeException exception) {
        if (!warned) {
            warned = true;
            plugin.getLogger().warning("Vault economy call failed: " + exception.getMessage());
        }
    }
}
