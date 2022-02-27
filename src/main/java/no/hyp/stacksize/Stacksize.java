package no.hyp.stacksize;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_18_R1.util.CraftMagicNumbers;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import manifold.ext.rt.api.Jailbreak;
import net.minecraft.world.item.Item;

public class Stacksize extends JavaPlugin implements Listener {

  private final String PERMISSION_VIEW = "stacksize.view";

  private final String PERMISSION_MODIFY = "stacksize.modify";

  private final String LOG_STACKSIZE_MODIFICATION = "STACK_SIZE_MODIFIED";

  private final String LOG_CONFIGURATION_MODIFICATION = "CONFIGURATION_MODIFIED";

  private final String SUBCOMMAND_VIEW = "view";

  private final String SUBCOMMAND_INSPECT = "inspect";

  private final String SUBCOMMAND_MODIFY = "modify";

  private final String SUBCOMMAND_RESET = "reset";

  /**
   * The path of the configuration directory.
   */
  private Path path;

  /**
   * A thread that polls for changes to the configuration.
   */
  private Thread watcherThread;

  /**
   * A list of currently modified materials and their original stack sizes.
   */
  private final Map<Material, Integer> vanillaStackSizes = new HashMap<>();
  ;

  @Override
  public void onEnable() {
    this.path = Paths.get(this.getDataFolder().getPath());
    // Create a configuration file in the plugin's directory if it does not exist.
    saveDefaultConfig();
    // Upgrade the configuration to the latest version if needed.
    this.configurationUpgrade();
    // Read configuration and modify the server's item stack sizes.
    this.reloadStackSizes(this.isLoggingStackSizeChanges());
    // Start a thread watching for configuration changes.
    this.configurationWatcherEnable();
    // Register the inventory updater.
    this.getServer().getPluginManager().registerEvents(this, this);
  }

  public void onDisable() {
    for (Material material : vanillaStackSizes.keySet()) {
      this.resetStackSize(material, this.isLoggingStackSizeChanges());
    }
    this.configurationWatcherDisable();
  }

  /**
   * Start/restart the configuration watcher thread.
   */
  public void configurationWatcherEnable() {
    //
    this.configurationWatcherDisable();
    this.watcherThread = new Thread(new ConfigurationWatcher(this, this.path));
    this.watcherThread.start();
  }

  /**
   * Stop the configuration watcher thread.
   */
  public void configurationWatcherDisable() {
    if (this.watcherThread != null) {
      this.watcherThread.interrupt();
    }
  }

  /**
   * Update the configuration from older versions to the newest.
   */
  public void configurationUpgrade() {
    // Find current configuration version. Assume missing config key means version 1.
    int version = this.getConfig().getInt("version", 1);
    // Version 2 is current.
    switch (version) {
      case 2:
        // Upgrade from version 1 to 2.
        break;
      case 1:
        this.getConfig().set("version", 2);
        this.getConfig().set("required", false);
        this.getConfig()
            .set("log", Arrays.asList(LOG_CONFIGURATION_MODIFICATION, LOG_STACKSIZE_MODIFICATION));
        this.saveConfig();

        // Otherwise, do nothing since upgrade method is unknown.
        break;
      default:
        break;
    }
  }

  /**
   * Read the modified stack sizes from the configuration.
   */
  public Map<Material, Integer> configurationReadMaterials() {
    Map<Material, Integer> stackSizes = new HashMap<>();
    // Parse the entries in the maxStackSize list and update the corresponding item max stack sizes.
    Set<String> materials;
    try {
      if (this.getConfig().contains("stackSizes")) {
        materials = getConfig().getConfigurationSection("stackSizes").getKeys(false);
      } else {
        this.getLogger().warning("Configuration is missing the key stackSizes.");
        return new HashMap<>();
      }
    } catch (NullPointerException e) {
      this.getLogger().warning("Unable to read keys.");
      return new HashMap<>();
    }
    for (String mMaterial : materials) {
      Material material = Material.matchMaterial(mMaterial);
      if (material == null) {
        this.getLogger()
            .warning(String.format("Unable to match \"%s\" to a material. Skipping.", mMaterial));
        continue;
      }
      int maxStackSize;
      try {
        maxStackSize = getConfig().getInt("stackSizes." + mMaterial);
      } catch (NumberFormatException e) {
        this.getLogger().warning(String.format("Unable to parse integer: \"%s\". Skipping.",
            getConfig().getString("stackSizes." + mMaterial)));
        continue;
      }
      stackSizes.put(material, maxStackSize);
    }
    return stackSizes;
  }

  /**
   * Write a material to the configuration.
   */
  public void configurationWriteMaterial(Material material, int size) {
    this.getConfig().set("stackSizes." + material.toString(), size);
  }

  public void configurationRemoveMaterial(Material material) {
    this.getConfig().set("stackSizes." + material.name(), null);
  }

  @Override
  public void saveConfig() {
    this.configurationWatcherDisable();
    super.saveConfig();
    this.configurationWatcherEnable();
  }

  /**
   * Loads/reloads stack sizes from the configuration and modifies the server stack sizes.
   *
   * @param log Log changes to the maximum stack sizes.
   */
  public void reloadStackSizes(boolean log) {
    // Reset stack sizes.
    for (Map.Entry<Material, Integer> entry : this.vanillaStackSizes.entrySet()) {
      Material material = entry.getKey();
      int stackSize = entry.getValue();
      modifyStackSize(material, stackSize, log);
    }
    // Read stack sizes.
    Map<Material, Integer> materials = this.configurationReadMaterials();
    // Modify stack sizes.
    for (Map.Entry<Material, Integer> material : materials.entrySet()) {
      modifyStackSize(material.getKey(), material.getValue(), log);
    }
  }

  /**
   * Reset a stack size back to its Vanilla size.
   */
  public boolean resetStackSize(Material material, boolean log) {
    if (vanillaStackSizes.containsKey(material)) {
      return modifyStackSize(material, vanillaStackSizes.get(material), log);
    } else {
      return true;
    }
  }

  /**
   * Modify the maximum stack sizes of an item on the server.
   *
   * @param material Item to change maximum stack size of.
   * @param size The new maximum stack size.
   * @param log Log changes to the maximum stack size.
   */
  public boolean modifyStackSize(Material material, int size, boolean log) {
    // Verify that the material is an item (that can be stored in an inventory).
    if (!material.isItem()) {
      this.getLogger().warning(String.format("%s is not an item.", material.name()));
      return false;
    }
    // Do nothing if the stack size is already correct.
    if (material.getMaxStackSize() == size) {
      if (log) {
        this.getLogger()
            .info(String.format("%s already has maximum stack size %d.", material.name(), size));
      }
      return true;
    }
    // Add the original stack size of a material to the map if it is not there.
    if (!this.vanillaStackSizes.containsKey(material)) {
      this.vanillaStackSizes.put(material, material.getMaxStackSize());
    }
    try {
      @Jailbreak
      Item item = CraftMagicNumbers.getItem(material);

      // Get the maxItemStack field in Item and change it.

      item.maxStackSize = size;
      @Jailbreak
      Material mat = material;
      mat.maxStack = size;
      if (log) {
        this.getLogger().info(
            String.format("Applied a maximum stack size of %d to %s.", size, material.name()));
      }
      return true;
    } catch (Exception ex) {
      ex.printStackTrace();
      this.getLogger().severe(
          String.format("Reflection error while modifying maximum stack size of %s.",
              material.name()));
      // If the server requires this plugin to work, shutdown the server.
      if (this.getConfig().getBoolean("required")) {
        this.getLogger().severe("Server requires plugin to work correctly. Shutting down server.");
        this.getServer().shutdown();
      }
      return false;
    }
  }

  @Override
  public boolean onCommand(@NotNull CommandSender sender, Command command, @NotNull String label,
      String[] arguments) {
    if (command.getName().equalsIgnoreCase("stacksize")) {
      if (arguments.length >= 1) {
        String subCommand = arguments[0];
        // view
        if (subCommand.equalsIgnoreCase(SUBCOMMAND_VIEW)) {
          if (sender.hasPermission(PERMISSION_VIEW)) {
            if (arguments.length == 2) {
              String materialName = arguments[1];
              try {
                Material material = Material.valueOf(materialName);
                sender.sendMessage(stringViewMaterial(material));
                return true;
              } catch (IllegalArgumentException e) {
                sender.sendMessage(stringInvalidMaterial(materialName));
                return true;
              }
            } else {
              sender.sendMessage(ChatColor.RED + "/stacksize view <material>");
              return true;
            }
          } else {
            sender.sendMessage(stringNoPermission(PERMISSION_VIEW));
            return true;
          }
          // A player can use the inspect subcommand to view stack size information about the item in hand.
        } else if (subCommand.equalsIgnoreCase(SUBCOMMAND_INSPECT)) {
          if (sender.hasPermission(PERMISSION_VIEW)) {
            if (arguments.length == 1) {
              if (sender instanceof Player player) {
                ItemStack item = player.getInventory().getItemInMainHand();
                Material material = item.getType();
                sender.sendMessage(stringViewMaterial(material));
              } else {
                sender.sendMessage(
                    ChatColor.RED + "You must be a player to inspect the item in your hand.");
              }
            } else {
              sender.sendMessage(ChatColor.RED + "/stacksize inspect <material>");
            }
            return true;
          } else {
            sender.sendMessage(stringNoPermission(PERMISSION_VIEW));
            return true;
          }
          // modify subcommand sets the stack size of a material and adds it to the configuration.
        } else if (subCommand.equalsIgnoreCase(SUBCOMMAND_MODIFY)) {
          if (sender.hasPermission(PERMISSION_MODIFY)) {
            if (arguments.length == 3) {
              String materialName = arguments[1];
              Material material;
              try {
                material = Material.valueOf(materialName);
              } catch (IllegalArgumentException e) {
                sender.sendMessage(stringInvalidMaterial(materialName));
                return true;
              }
              if (!material.isItem()) {
                sender.sendMessage(stringMaterialNotItem(materialName));
                return true;
              }
              int oldSize = material.getMaxStackSize();
              int stackSize;
              try {
                stackSize = Integer.parseInt(arguments[2]);
              } catch (NumberFormatException e) {
                sender.sendMessage(stringInvalidInteger(arguments[2]));
                return true;
              }
              this.configurationWriteMaterial(material, stackSize);
              this.saveConfig();
              this.reloadStackSizes(this.isLoggingStackSizeChanges());
              sender.sendMessage(this.stringModifiedStackSize(materialName, oldSize, stackSize));
            } else {
              sender.sendMessage(ChatColor.RED + "/stacksize modify <material> <stacksize>");
            }
          } else {
            sender.sendMessage(stringNoPermission(PERMISSION_MODIFY));
          }
          return true;
          // reset command resets a stack size of a material to the Vanilla size and removes it from the configuration.
        } else if (subCommand.equalsIgnoreCase(SUBCOMMAND_RESET)) {
          if (sender.hasPermission(PERMISSION_MODIFY)) {
            if (arguments.length == 2) {
              String materialName = arguments[1];
              Material material;
              try {
                material = Material.valueOf(materialName);
              } catch (IllegalArgumentException e) {
                sender.sendMessage(stringInvalidMaterial(materialName));
                return true;
              }
              if (!material.isItem()) {
                sender.sendMessage(stringMaterialNotItem(materialName));
                return true;
              }
              int oldSize = material.getMaxStackSize();
              int newSize =
                  vanillaStackSizes.containsKey(material) ? vanillaStackSizes.get(material)
                      : material.getMaxStackSize();
              this.configurationRemoveMaterial(material);
              this.saveConfig();
              this.reloadStackSizes(this.isLoggingStackSizeChanges());
              sender.sendMessage(this.stringResetStackSize(materialName, oldSize, newSize));
            } else {
              sender.sendMessage(ChatColor.RED + "/stacksize reset <material>");
            }
          } else {
            sender.sendMessage(stringNoPermission(PERMISSION_MODIFY));
          }
          return true;
        } else {
          sender.sendMessage(stringSubCommands());
          return true;
        }
      } else {
        sender.sendMessage(stringSubCommands());
        return true;
      }
    } else {
      return false;
    }
  }

  public boolean isLoggingConfigurationModification() {
    return this.getConfig().getStringList("log").contains(LOG_CONFIGURATION_MODIFICATION);
  }

  public boolean isLoggingStackSizeChanges() {
    return this.getConfig().getStringList("log").contains(LOG_STACKSIZE_MODIFICATION);
  }

  @Override
  public List<String> onTabComplete(@NotNull CommandSender sender, Command command,
      @NotNull String alias, String[] arguments) {
    if (command.getName().equalsIgnoreCase("stacksize")) {
      if (arguments.length == 0) {
        return new ArrayList<>();
      } else if (arguments.length == 1) {
        List<String> subCommands = new ArrayList<>();
        subCommands.add(SUBCOMMAND_VIEW);
        subCommands.add(SUBCOMMAND_INSPECT);
        subCommands.add(SUBCOMMAND_MODIFY);
        subCommands.add(SUBCOMMAND_RESET);
        String subCommand = arguments[0];
        return subCommands.stream().filter(x -> x.startsWith(subCommand.toLowerCase()))
            .collect(Collectors.toList());
      } else if (arguments.length == 2) {
        String subCommand = arguments[0];
        if (subCommand.equalsIgnoreCase(SUBCOMMAND_VIEW) || subCommand.equalsIgnoreCase(
            SUBCOMMAND_MODIFY) || subCommand.equalsIgnoreCase(SUBCOMMAND_RESET)) {
          String mMaterial = arguments[1];
          return Arrays.stream(Material.values()).filter(x ->
              x.name().startsWith(mMaterial.toUpperCase()) && x.isItem()
          ).map(
              Material::name).collect(Collectors.toList()
          );
        } else {
          return new ArrayList<>();
        }
      } else if (arguments.length == 3) {
        String subCommand = arguments[0];
        if (subCommand.equalsIgnoreCase(SUBCOMMAND_MODIFY)) {
          return List.of("1", "4", "8", "16", "32", "64");
        }
      }
    } else {
      return new ArrayList<>();
    }
    return new ArrayList<>();
  }

  /**
   * This listener will update the player's inventory the tick after an inventory click. This is
   * required since the client predicts how the inventory will look afterwards. When the server has
   * modified stack sizes the prediction might be wrong.
   */
  @EventHandler(priority = EventPriority.MONITOR)
  public void onInventoryClick(InventoryClickEvent event) {
    if (event.isCancelled()) {
      return;
    }
    if (event.getWhoClicked() instanceof Player player) {
      // The creative inventory works differently to the survival inventory and will not work
      // properly when updating the inventory the tick after a click.
      if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
        Bukkit.getScheduler().runTask(this, player::updateInventory);
      }
    }
  }

  private String stringModifiedStackSize(String materialName, int oldSize, int newSize) {
    return String.format(
        ChatColor.YELLOW + "Modified maximum stack size of " + ChatColor.RESET + "%s"
            + ChatColor.YELLOW + " from " + ChatColor.RESET + "%d" + ChatColor.YELLOW + " to "
            + ChatColor.RESET + "%d" + ChatColor.YELLOW + ".", materialName, oldSize, newSize);
  }

  private String stringResetStackSize(String materialName, int oldSize, int newSize) {
    return String.format(ChatColor.YELLOW + "Reset maximum stack size of " + ChatColor.RESET + "%s"
            + ChatColor.YELLOW + " from " + ChatColor.RESET + "%d" + ChatColor.YELLOW
            + " to Vanilla size " + ChatColor.RESET + "%d" + ChatColor.YELLOW + ".", materialName,
        oldSize, newSize);
  }

  private String stringMaterialNotItem(String materialName) {
    return String.format(
        ChatColor.RESET + "%s" + ChatColor.RED + " is not an item." + ChatColor.RED, materialName);
  }

  private String stringInvalidInteger(String integer) {
    return String.format(
        ChatColor.RED + "Invalid integer: " + ChatColor.RESET + "%s" + ChatColor.RED + ".",
        integer);
  }

  private String stringInvalidMaterial(String materialName) {
    return String.format(ChatColor.RESET + "%s" + ChatColor.RED + " is not a valid material.",
        materialName);
  }

  private String stringViewMaterial(Material material) {
    int max = material.getMaxStackSize();
    int originalSize = vanillaStackSizes.containsKey(material) ? vanillaStackSizes.get(material)
        : material.getMaxStackSize();
    return String.format(ChatColor.YELLOW + "Material: " + ChatColor.RESET + "%s" + ChatColor.YELLOW
            + "\n · Maximum Stack Size: " + ChatColor.RESET + "%2d" + ChatColor.YELLOW
            + "\n · Vanilla Maximum Stack Size: " + ChatColor.RESET + "%2d", material.name(), max,
        originalSize);
  }

  private String stringSubCommands() {
    return ChatColor.RED + "/stacksize <view | inspect | modify | reset>";
  }

  private String stringNoPermission(String permission) {
    return String.format(
        ChatColor.RED + "You lack the permission " + ChatColor.RESET + "%s" + ChatColor.RED
            + " to execute that command.", permission);
  }

}
