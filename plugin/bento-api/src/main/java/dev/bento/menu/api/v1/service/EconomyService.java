package dev.bento.menu.api.v1.service;

import org.bukkit.entity.Player;

/**
 * Economy abstraction backed by Vault. Methods may be called from the owner
 * thread; implementations must not block the tick thread unnecessarily.
 */
public interface EconomyService {

    boolean available();

    double balance(Player player);

    String format(double amount);

    TransactionResult withdraw(Player player, double amount);

    TransactionResult deposit(Player player, double amount);
}
