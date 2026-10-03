package org.teamzetaverse.launcher.cosmetics;

import com.google.gson.JsonArray;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.teamzetaverse.cosmetics.api.Cosmetic;
import org.teamzetaverse.launcher.util.Json;

/** An identity's saved item selection. Prices always come from the current signed catalogue. */
public final class CosmeticBasket {
    public static final int MAX_ITEMS = 100;
    private final Path path;
    private final Set<String> ids = new LinkedHashSet<>();

    public record Contents(List<Cosmetic> items, List<String> unavailable, long totalCents, String currency, boolean mixedCurrencies) {
        public boolean canCheckout() {
            return !this.items.isEmpty() && this.unavailable.isEmpty() && !this.mixedCurrencies;
        }
        public List<String> ids() { return this.items.stream().map(Cosmetic::id).toList(); }
    }

    public CosmeticBasket(final Path path) throws IOException {
        this.path = path;
        if (!Files.isRegularFile(path)) return;
        if (Files.size(path) > 16384) throw new IOException("Saved cosmetics basket is too large.");
        var root = Json.readObject(path);
        var items = root.get("cosmeticIds");
        if (items == null || !items.isJsonArray() || items.getAsJsonArray().size() > MAX_ITEMS) {
            throw new IOException("Saved cosmetics basket is invalid.");
        }
        for (var item : items.getAsJsonArray()) {
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()
                || !item.getAsString().matches("[a-z0-9_]{1,32}:[a-z0-9_]{1,64}")) {
                throw new IOException("Saved cosmetics basket contains an invalid item.");
            }
            this.ids.add(item.getAsString());
        }
    }

    public List<String> ids() { return List.copyOf(this.ids); }
    public boolean contains(final String id) { return this.ids.contains(id); }

    public Contents contents(final Collection<Cosmetic> catalogue) {
        Map<String, Cosmetic> available = new LinkedHashMap<>();
        for (Cosmetic item : catalogue) available.put(item.id(), item);
        List<Cosmetic> items = new ArrayList<>();
        List<String> unavailable = new ArrayList<>();
        long total = 0;
        String currency = null;
        boolean mixed = false;
        for (String id : this.ids) {
            Cosmetic item = available.get(id);
            if (item == null || item.unlock().purchase().isEmpty()) {
                unavailable.add(id);
                continue;
            }
            var purchase = item.unlock().purchase().get();
            total = Math.addExact(total, purchase.priceCents());
            if (currency == null) currency = purchase.currency();
            else if (!currency.equals(purchase.currency())) mixed = true;
            items.add(item);
        }
        return new Contents(List.copyOf(items), List.copyOf(unavailable), total, currency, mixed);
    }

    public void add(final Cosmetic item, final Collection<Cosmetic> catalogue) throws IOException {
        if (this.contains(item.id())) return;
        if (this.ids.size() >= MAX_ITEMS) throw new IOException("The basket can hold up to 100 different cosmetics.");
        if (item.unlock().purchase().isEmpty()) throw new IOException("This cosmetic is not for sale.");
        Contents current = this.contents(catalogue);
        if (current.currency() != null && !current.currency().equals(item.unlock().purchase().get().currency())) {
            throw new IOException("Basket items must use the same currency. Check out or clear your current basket first.");
        }
        Set<String> next = new LinkedHashSet<>(this.ids);
        next.add(item.id());
        this.save(next);
    }

    public void removeAll(final Collection<String> removed) throws IOException {
        Set<String> next = new LinkedHashSet<>(this.ids);
        if (next.removeAll(removed)) this.save(next);
    }

    public void clear() throws IOException { this.removeAll(this.ids()); }

    private void save(final Set<String> next) throws IOException {
        var root = new com.google.gson.JsonObject();
        JsonArray items = new JsonArray();
        next.forEach(items::add);
        root.add("cosmeticIds", items);
        Json.write(this.path, root);
        this.ids.clear();
        this.ids.addAll(next);
    }
}
