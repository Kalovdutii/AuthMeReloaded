package fr.xephi.authme;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

import me.muizers.Notifications.Notifications;
import net.citizensnpcs.Citizens;
import net.milkbowl.vault.permission.Permission;

import org.apache.logging.log4j.LogManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import com.earth2me.essentials.Essentials;
import com.maxmind.geoip.LookupService;
import com.onarandombox.MultiverseCore.MultiverseCore;

import fr.xephi.authme.api.API;
import fr.xephi.authme.cache.auth.PlayerAuth;
import fr.xephi.authme.cache.auth.PlayerCache;
import fr.xephi.authme.cache.backup.FileCache;
import fr.xephi.authme.cache.limbo.LimboCache;
import fr.xephi.authme.cache.limbo.LimboPlayer;
import fr.xephi.authme.commands.AdminCommand;
import fr.xephi.authme.commands.CaptchaCommand;
import fr.xephi.authme.commands.ChangePasswordCommand;
import fr.xephi.authme.commands.EmailCommand;
import fr.xephi.authme.commands.LoginCommand;
import fr.xephi.authme.commands.LogoutCommand;
import fr.xephi.authme.commands.PasspartuCommand;
import fr.xephi.authme.commands.RegisterCommand;
import fr.xephi.authme.commands.UnregisterCommand;
import fr.xephi.authme.datasource.CacheDataSource;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.datasource.FileDataSource;
import fr.xephi.authme.datasource.MySQLDataSource;
import fr.xephi.authme.datasource.SqliteDataSource;
import fr.xephi.authme.datasource.MongoDataSource;
import fr.xephi.authme.listener.AuthMeBlockListener;
import fr.xephi.authme.listener.AuthMeChestShopListener;
import fr.xephi.authme.listener.AuthMeEntityListener;
import fr.xephi.authme.listener.AuthMePlayerListener;
import fr.xephi.authme.listener.AuthMeServerListener;
import fr.xephi.authme.listener.AuthMeSpoutListener;
import fr.xephi.authme.plugin.manager.BungeeCordMessage;
import fr.xephi.authme.plugin.manager.CitizensCommunicator;
import fr.xephi.authme.plugin.manager.CombatTagComunicator;
import fr.xephi.authme.plugin.manager.EssSpawn;
import fr.xephi.authme.process.Management;
import fr.xephi.authme.settings.Messages;
import fr.xephi.authme.settings.PlayersLogs;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.Spawn;
import fr.xephi.authme.threads.FlatFileThread;
import fr.xephi.authme.threads.MySQLThread;
import fr.xephi.authme.threads.SQLiteThread;

public class AuthMe extends JavaPlugin {

    public DataSource database = null;
    private Settings settings;
	private Messages m;
    public PlayersLogs pllog;
    public static Server server;
    public static Logger authmeLogger = Logger.getLogger("AuthMe");
    public static AuthMe authme;
    public Permission permission;
    private Utils utils = Utils.getInstance();
    private JavaPlugin plugin;
    private FileCache playerBackup = new FileCache();
	public CitizensCommunicator citizens;
	public SendMailSSL mail = null;
	public int CitizensVersion = 0;
	public int CombatTag = 0;
	public double ChestShop = 0;
	public boolean BungeeCord = false;
	public Essentials ess;
	public Notifications notifications;
	public API api;
	public Management management;
    public HashMap<String, Integer> captcha = new HashMap<String, Integer>();
    public HashMap<String, String> cap = new HashMap<String, String>();
    public HashMap<String, String> realIp = new HashMap<String, String>();
	public MultiverseCore multiverse = null;
	public Location essentialsSpawn;
	public Thread databaseThread = null;
	public LookupService ls = null;
	public boolean antibotMod = false;
	public boolean delayedAntiBot = true;

	public Settings getSettings() {
		return settings;
	}

	@Override
    public void onEnable() {
    	authme = this;

    	authmeLogger.setParent(this.getLogger());

    	settings = new Settings(this);
    	settings.loadConfigOptions();

    	citizens = new CitizensCommunicator(this);

        if (Settings.enableAntiBot) {
        	Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
				@Override
				public void run() {
					delayedAntiBot = false;
				}
        	}, 2400);
        }

    	m = Messages.getInstance();

        pllog = PlayersLogs.getInstance();

        server = getServer();

        //Find Permissions
        checkVault();

        //Set Console Filter
        if (Settings.removePassword) {
        	this.getLogger().setFilter(new ConsoleFilter());
        	Bukkit.getLogger().setFilter(new ConsoleFilter());
        	Logger.getLogger("Minecraft").setFilter(new ConsoleFilter());
        	authmeLogger.setFilter(new ConsoleFilter());
        	// Set Log4J Filter
        	try {
        		Class.forName("org.apache.logging.log4j.core.Filter");
            	setLog4JFilter();
        	} catch (ClassNotFoundException e) {
        		ConsoleLogger.info("You're using Minecraft 1.6.x or older, Log4J support is disabled");
        	} catch (NoClassDefFoundError e) {
        		ConsoleLogger.info("You're using Minecraft 1.6.x or older, Log4J support is disabled");
        	}
        }

        //Load MailApi
        if(!Settings.getmailAccount.isEmpty() && !Settings.getmailPassword.isEmpty())
        	mail = new SendMailSSL(this);

		//Check Citizens Version
		citizensVersion();

		//Check Combat Tag Version
		combatTag();

		//Check Notifications
		checkNotifications();

		//Check Multiverse
		checkMultiverse();

		//Check ChestShop
		checkChestShop();

		//Check Essentials
		checkEssentials();

        /*
         *  Back style on start if avalaible
         */
        if(Settings.isBackupActivated && Settings.isBackupOnStart) {
        Boolean Backup = new PerformBackup(this).DoBackup();
        if(Backup) ConsoleLogger.info("Backup Complete");
            else ConsoleLogger.showError("Error while making Backup");
        }

        /*
         * Backend MYSQL - FILE - SQLITE
         */
        switch (Settings.getDataSource) {
            case FILE:
            	if (Settings.useMultiThreading) {
                    FlatFileThread fileThread = new FlatFileThread();
                    fileThread.start();
                    database = fileThread;
                    databaseThread = fileThread;
                    break;
            	}
                try {
                    database = new FileDataSource();
                } catch (Exception ex) {
                    ConsoleLogger.showError(ex.getMessage());
                    if (Settings.isStopEnabled) {
                    	ConsoleLogger.showError("Can't use FLAT FILE... SHUTDOWN...");
                    	server.shutdown();
                    }
                    if (!Settings.isStopEnabled)
                    this.getServer().getPluginManager().disablePlugin(this);
                    return;
                }
                break;
            case MYSQL:
            	if (Settings.useMultiThreading) {
                    MySQLThread sqlThread = new MySQLThread();
                    sqlThread.start();
                    database = sqlThread;
                    databaseThread = sqlThread;
                    break;
            	}
                try {
                    database = new MySQLDataSource();
                } catch (Exception ex) {
                    ConsoleLogger.showError(ex.getMessage());
                    if (Settings.isStopEnabled) {
                    	ConsoleLogger.showError("Can't use MySQL... Please input correct MySQL informations ! SHUTDOWN...");
                    	server.shutdown();
                    }
                    if (!Settings.isStopEnabled)
                    this.getServer().getPluginManager().disablePlugin(this);
                    return;
                }
                break;
            case SQLITE:
            	if (Settings.useMultiThreading) {
                    SQLiteThread sqliteThread = new SQLiteThread();
                    sqliteThread.start();
                    database = sqliteThread;
                    databaseThread = sqliteThread;
                    break;
            	}
                try {
                     database = new SqliteDataSource();
                } catch (Exception ex) {
                    ConsoleLogger.showError(ex.getMessage());
                    if (Settings.isStopEnabled) {
                    	ConsoleLogger.showError("Can't use SQLITE... ! SHUTDOWN...");
                    	server.shutdown();
                    }
                    if (!Settings.isStopEnabled)
                    this.getServer().getPluginManager().disablePlugin(this);
                    return;
                }
                break;
             case MONGO:
            	 try {
            		 database = new MongoDataSource();
            	 } catch(Exception ex) {
            		 ConsoleLogger.showError(ex.getMessage());
            		 if(Settings.isStopEnabled) {
            			 ConsoleLogger.showError("Can't use MONGO! Shutdown.");
            			 server.shutdown();
            		 }
            		 else {
            			 this.getServer().getPluginManager().disablePlugin(this);
            		 }
            		 return;
            	 }
            	 break;
        }

        if (Settings.isCachingEnabled) {
            database = new CacheDataSource(this, database);
        }

        // Setup API
    	api = new API(this, database);

		// Setup Management
		management = new Management(database, this);
		management.start();

        PluginManager pm = getServer().getPluginManager();
        if (Settings.bungee) {
        	Bukkit.getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        	Bukkit.getMessenger().registerIncomingPluginChannel(this, "BungeeCord", new BungeeCordMessage(this));
        }
        if (pm.isPluginEnabled("Spout")) {
        	pm.registerEvents(new AuthMeSpoutListener(database), this);
        	ConsoleLogger.info("Successfully hook with Spout!");
        }
        pm.registerEvents(new AuthMePlayerListener(this,database),this);
        pm.registerEvents(new AuthMeBlockListener(database, this),this);
        pm.registerEvents(new AuthMeEntityListener(database, this),this);
        pm.registerEvents(new AuthMeServerListener(this), this);
        if (ChestShop != 0) {
        	pm.registerEvents(new AuthMeChestShopListener(database, this), this);
        	ConsoleLogger.info("Successfully hook with ChestShop!");
        }

        this.getCommand("authme").setExecutor(new AdminCommand(this, database));
        this.getCommand("register").setExecutor(new RegisterCommand(this));
        this.getCommand("login").setExecutor(new LoginCommand(this));
        this.getCommand("changepassword").setExecutor(new ChangePasswordCommand(database, this));
        this.getCommand("logout").setExecutor(new LogoutCommand(this,database));
        this.getCommand("unregister").setExecutor(new UnregisterCommand(this, database));
        this.getCommand("passpartu").setExecutor(new PasspartuCommand(this));
        this.getCommand("email").setExecutor(new EmailCommand(this, database));
        this.getCommand("captcha").setExecutor(new CaptchaCommand(this));

        if(!Settings.isForceSingleSessionEnabled) {
            ConsoleLogger.showError("ATTENTION by disabling ForceSingleSession, your server protection is set to low");
        }

        if (Settings.reloadSupport)
        	try {
                onReload();
                if (server.getOnlinePlayers().length < 1) {
                	try {
                    	database.purgeLogged();
                	} catch (NullPointerException npe) {
                	}
                }
        	} catch (NullPointerException ex) {
        	}

        if (Settings.usePurge)
        	autoPurge();

        // Download GeoIp.dat file
        downloadGeoIp();

        // Start Email recall task if needed
        recallEmail();

        ConsoleLogger.info("Authme " + this.getDescription().getVersion() + " enabled");
    }

	private void setLog4JFilter() {
		Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
			@Override
			public void run() {
	        	org.apache.logging.log4j.core.Logger coreLogger = (org.apache.logging.log4j.core.Logger) LogManager.getRootLogger();
	        	coreLogger.addFilter(new Log4JFilter());
			}
		});
	}

	public void checkVault() {
        if (this.getServer().getPluginManager().getPlugin("Vault") != null && this.getServer().getPluginManager().getPlugin("Vault").isEnabled()) {
            RegisteredServiceProvider<Permission> permissionProvider =
                getServer().getServicesManager().getRegistration(net.milkbowl.vault.permission.Permission.class);
            if (permissionProvider != null) {
            	permission = permissionProvider.getProvider();
            	ConsoleLogger.info("Vault plugin detected, hook with " + permission.getName() + " system");
            } else {
            	ConsoleLogger.showError("Vault plugin is detected but not the permissions plugin!");
            }
        } else {
        	permission = null;
        }
	}

	public void checkChestShop() {
    	if (!Settings.chestshop) {
    		this.ChestShop = 0;
    		return;
    	}
    	if (this.getServer().getPluginManager().getPlugin("ChestShop") != null && this.getServer().getPluginManager().getPlugin("ChestShop").isEnabled()) {
    		try {
				String ver = com.Acrobot.ChestShop.ChestShop.getVersion();
				try {
					double version = Double.valueOf(ver.split(" ")[0]);
	    			if (version >= 3.50) {
	    				this.ChestShop = version;
	    			} else {
	    				ConsoleLogger.showError("Please Update your ChestShop version!");
	    			}
				} catch (NumberFormatException nfe) {
					try {
						double version = Double.valueOf(ver.split("t")[0]);
		    			if (version >= 3.50) {
		    				this.ChestShop = version;
		    			} else {
		    				ConsoleLogger.showError("Please Update your ChestShop version!");
		    			}
					} catch (NumberFormatException nfee) {
					}
				}
    		} catch (Exception e) {}
    	} else {
    		this.ChestShop = 0;
    	}
	}

	public void checkMultiverse() {
		if(!Settings.multiverse) {
			multiverse = null;
			return;
		}
    	if (this.getServer().getPluginManager().getPlugin("Multiverse-Core") != null && this.getServer().getPluginManager().getPlugin("Multiverse-Core").isEnabled()) {
    		try {
    			multiverse  = (MultiverseCore) this.getServer().getPluginManager().getPlugin("Multiverse-Core");
    			ConsoleLogger.info("Hook with Multiverse-Core for SpawnLocations");
    		} catch (NullPointerException npe) {
    			multiverse = null;
    		} catch (ClassCastException cce) {
    			multiverse = null;
    		} catch (NoClassDefFoundError ncdfe) {
    			multiverse = null;
    		}
    	} else {
    		multiverse = null;
    	}
	}

	public void checkEssentials() {
    	if (this.getServer().getPluginManager().getPlugin("Essentials") != null && this.getServer().getPluginManager().getPlugin("Essentials").isEnabled()) {
    		try {
    			ess  = (Essentials) this.getServer().getPluginManager().getPlugin("Essentials");
    			ConsoleLogger.info("Hook with Essentials plugin");
    		} catch (NullPointerException npe) {
    			ess = null;
    		} catch (ClassCastException cce) {
    			ess = null;
    		} catch (NoClassDefFoundError ncdfe) {
    			ess = null;
    		}
    	} else {
    		ess = null;
    	}
    	if (this.getServer().getPluginManager().getPlugin("EssentialsSpawn") != null && this.getServer().getPluginManager().getPlugin("EssentialsSpawn").isEnabled()) {
    		try {
        		essentialsSpawn = new EssSpawn().getLocation();
        		ConsoleLogger.info("Hook with EssentialsSpawn plugin");
    		} catch (Exception e) {
    			essentialsSpawn = null;
    			ConsoleLogger.showError("Error while reading /plugins/Essentials/spawn.yml file ");
    		}
    	} else {
    		ess = null;
    	}
	}

	public void checkNotifications() {
		if (!Settings.notifications) {
			this.notifications = null;
			return;
		}
		if (this.getServer().getPluginManager().getPlugin("Notifications") != null && this.getServer().getPluginManager().getPlugin("Notifications").isEnabled()) {
			this.notifications = (Notifications) this.getServer().getPluginManager().getPlugin("Notifications");
			ConsoleLogger.info("Successfully hook with Notifications");
		} else {
			this.notifications = null;
		}
	}

	public void combatTag() {
		if (this.getServer().getPluginManager().getPlugin("CombatTag") != null && this.getServer().getPluginManager().getPlugin("CombatTag").isEnabled()) {
			this.CombatTag = 1;
		} else {
			this.CombatTag = 0;
		}
	}

	public void citizensVersion() {
		if (this.getServer().getPluginManager().getPlugin("Citizens") != null && this.getServer().getPluginManager().getPlugin("Citizens").isEnabled()) {
			Citizens cit = (Citizens) this.getServer().getPluginManager().getPlugin("Citizens");
            String ver = cit.getDescription().getVersion();
            String[] args = ver.split("\\.");
            if (args[0].contains("1")) {
            	this.CitizensVersion = 1;
            } else {
            	this.CitizensVersion = 2;
            }
		} else {
			this.CitizensVersion = 0;
		}
	}

	@Override
    public void onDisable() {
        if (Bukkit.getOnlinePlayers().length != 0)
        	for(Player player : Bukkit.getOnlinePlayers()) {
        		this.savePlayer(player);
        	}

        if (database != null) {
            database.close();
        }

        if (databaseThread != null) {
        	databaseThread.interrupt();
        }

        if(Settings.isBackupActivated && Settings.isBackupOnStop) {
        	Boolean Backup = new PerformBackup(this).DoBackup();
        	if(Backup) ConsoleLogger.info("Backup Complete");
            	else ConsoleLogger.showError("Error while making Backup");
        }
        ConsoleLogger.info("Authme " + this.getDescription().getVersion() + " disabled");
    }

	private void onReload() {
		try {
	    	if (Bukkit.getServer().getOnlinePlayers() != null) {
	    		for (Player player : Bukkit.getServer().getOnlinePlayers()) {
	    			if (database.isLogged(player.getName().toLowerCase())) {
	    				String name = player.getName().toLowerCase();
	    		        PlayerAuth pAuth = database.getAuth(name);
	    	            if(pAuth == null)
	    	                break;
	    	            PlayerAuth auth = new PlayerAuth(name, pAuth.getHash(), pAuth.getIp(), new Date().getTime(), pAuth.getEmail(), player.getName());
	    	            database.updateSession(auth);
	    				PlayerCache.getInstance().addPlayer(auth);
	    			}
	    		}
	    	}
	    	return;
		} catch (NullPointerException ex) {
			return;
		}
    }

	public static AuthMe getInstance() {
		return authme;
	}

	public void savePlayer(Player player) throws IllegalStateException, NullPointerException {
		try {
	      if ((citizens.isNPC(player, this)) || (Utils.getInstance().isUnrestricted(player)) || (CombatTagComunicator.isNPC(player))) {
	          return;
	        }
		} catch (Exception e) { }
		try {
	        String name = player.getName().toLowerCase();
	        if (PlayerCache.getInstance().isAuthenticated(name) && !player.isDead() && Settings.isSaveQuitLocationEnabled) {
	        	final PlayerAuth auth = new PlayerAuth(player.getName().toLowerCase(), player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ(), player.getWorld().getName());
				database.updateQuitLoc(auth);
	        }
	        if (LimboCache.getInstance().hasLimboPlayer(name))
	        {
	          LimboPlayer limbo = LimboCache.getInstance().getLimboPlayer(name);
	          if (Settings.protectInventoryBeforeLogInEnabled.booleanValue()) {
	            player.getInventory().setArmorContents(limbo.getArmour());
	            player.getInventory().setContents(limbo.getInventory());
	          }
	          if (!limbo.getLoc().getChunk().isLoaded()) {
	        	  limbo.getLoc().getChunk().load();
	          }
	          player.teleport(limbo.getLoc());
	          this.utils.addNormal(player, limbo.getGroup());
	          player.setOp(limbo.getOperator());
	          this.plugin.getServer().getScheduler().cancelTask(limbo.getTimeoutTaskId());
	          LimboCache.getInstance().deleteLimboPlayer(name);
	          if (this.playerBackup.doesCacheExist(name)) {
	            this.playerBackup.removeCache(name);
	          }
	        }
	        PlayerCache.getInstance().removePlayer(name);
	        database.setUnlogged(name);
	        player.saveData();
	      } catch (Exception ex) {
	      }
	}

	public CitizensCommunicator getCitizensCommunicator() {
		return citizens;
	}

	public void setMessages(Messages m) {
		this.m = m;
	}

	public Messages getMessages() {
		return m;
	}

	public Player generateKickPlayer(Player[] players) {
		Player player = null;
		int i;
		for (i = 0 ; i <= players.length ; i++) {
			Random rdm = new Random();
			int a = rdm.nextInt(players.length);
			if (!(authmePermissible(players[a], "authme.vip"))) {
				player = players[a];
				break;
			}
		}
		if (player == null) {
			for (Player p : players) {
				if (!(authmePermissible(p, "authme.vip"))) {
					player = p;
					break;
				}
			}
		}
		return player;
	}

	public boolean authmePermissible(Player player, String perm) {
		if (player.hasPermission(perm))
			return true;
		else if (permission != null) {
			return permission.playerHas(player, perm);
		}
		return false;
	}

	public boolean authmePermissible(CommandSender sender, String perm) {
		if (sender.hasPermission(perm)) return true;
		else if (permission != null) {
			return permission.has(sender, perm);
		}
		return false;
	}

	private void autoPurge() {
		if (!Settings.usePurge) {
			return;
		}
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, -(Settings.purgeDelay));
        long until = calendar.getTimeInMillis();
		List<String> cleared = this.database.autoPurgeDatabase(until);
		ConsoleLogger.info("AutoPurgeDatabase : " + cleared.size() + " accounts removed.");
		if (cleared.isEmpty())
			return;
		if (Settings.purgeEssentialsFile && this.ess != null)
			purgeEssentials(cleared);
		if (Settings.purgePlayerDat)
			purgeDat(cleared);
		if (Settings.purgeLimitedCreative)
			purgeLimitedCreative(cleared);
		if (Settings.purgeAntiXray)
			purgeAntiXray(cleared);
	}

	public void purgeAntiXray(List<String> cleared) {
		int i = 0;
		for (String name : cleared) {
			org.bukkit.OfflinePlayer player = Bukkit.getOfflinePlayer(name);
			if (player == null) continue;
			String playerName = player.getName();
			File playerFile = new File("." + File.separator + "plugins" + File.separator + "AntiXRayData" + File.separator + "PlayerData" + File.separator + playerName);
			if (playerFile.exists()) {
				playerFile.delete();
				i++;
			}
		}
		ConsoleLogger.info("AutoPurgeDatabase : Remove " + i + " AntiXRayData Files");
	}

	public void purgeLimitedCreative(List<String> cleared) {
		int i = 0;
		for (String name : cleared) {
			org.bukkit.OfflinePlayer player = Bukkit.getOfflinePlayer(name);
			if (player == null) continue;
			String playerName = player.getName();
			File playerFile = new File("." + File.separator + "plugins" + File.separator + "LimitedCreative" + File.separator + "inventories" + File.separator + playerName + ".yml");
			if (playerFile.exists()) {
				playerFile.delete();
				i++;
			}
			playerFile = new File("." + File.separator + "plugins" + File.separator + "LimitedCreative" + File.separator + "inventories" + File.separator +  playerName + "_creative.yml");
			if (playerFile.exists()) {
				playerFile.delete();
				i++;
			}
			playerFile = new File("." + File.separator + "plugins" + File.separator + "LimitedCreative" + File.separator + "inventories" + File.separator +  playerName + "_adventure.yml");
			if (playerFile.exists()) {
				playerFile.delete();
				i++;
			}
		}
		ConsoleLogger.info("AutoPurgeDatabase : Remove " + i + " LimitedCreative Survival, Creative and Adventure files");
	}

	public void purgeDat(List<String> cleared) {
		int i = 0;
		for (String name : cleared) {
			org.bukkit.OfflinePlayer player = Bukkit.getOfflinePlayer(name);
			if (player == null) continue;
			String playerName = player.getName();
			File playerFile = new File (this.getServer().getWorldContainer() + File.separator + Settings.defaultWorld + File.separator + "players" + File.separator + playerName + ".dat");
			if (playerFile.exists()) {
				playerFile.delete();
				i++;
			}
		}
		ConsoleLogger.info("AutoPurgeDatabase : Remove " + i + " .dat Files");
	}

	public void purgeEssentials(List<String> cleared) {
		int i = 0;
		for (String name : cleared) {
			File playerFile = new File(this.ess.getDataFolder() + File.separator + "userdata" + File.separator + name + ".yml");
			if (playerFile.exists()) {
				playerFile.delete();
				i++;
			}
		}
		ConsoleLogger.info("AutoPurgeDatabase : Remove " + i + " EssentialsFiles");
	}

    public Location getSpawnLocation(Player player, World world) {
    	String[] spawnPriority = Settings.spawnPriority.split(",");
        Location spawnLoc = world.getSpawnLocation();
        int i = 3;
        for (i = 3 ; i >= 0 ; i--) {
        	String s = spawnPriority[i];
        	if (s.equalsIgnoreCase("default") && getDefaultSpawn(world) != null)
        		spawnLoc = getDefaultSpawn(world);
        	if (s.equalsIgnoreCase("multiverse") && getMultiverseSpawn(world) != null)
        		spawnLoc = getMultiverseSpawn(world);
        	if (s.equalsIgnoreCase("essentials") && getEssentialsSpawn() != null)
        		spawnLoc = getEssentialsSpawn();
        	if (s.equalsIgnoreCase("authme") && getAuthMeSpawn(player) != null)
        		spawnLoc = getAuthMeSpawn(player);
        }
        if (spawnLoc == null)
        	spawnLoc = world.getSpawnLocation();
        return spawnLoc;
    }

    private Location getDefaultSpawn(World world) {
    	return world.getSpawnLocation();
    }

    private Location getMultiverseSpawn(World world) {
        if (multiverse != null && Settings.multiverse) {
            try {
                return multiverse.getMVWorldManager().getMVWorld(world).getSpawnLocation();
            } catch (NullPointerException npe) {
            } catch (ClassCastException cce) {
            } catch (NoClassDefFoundError ncdfe) {
            }
        }
        return null;
    }

    private Location getEssentialsSpawn() {
        if (essentialsSpawn != null) {
            return essentialsSpawn;
        }
        return null;
    }
    
    private Location getAuthMeSpawn(Player player) {
        if ((!database.isAuthAvailable(player.getName().toLowerCase()) || !player.hasPlayedBefore()) && Spawn.getInstance().getFirstSpawn() != null)
        	return Spawn.getInstance().getFirstSpawn();
        if (Spawn.getInstance().getSpawn() != null)
            return Spawn.getInstance().getSpawn();
        return null;
    }

    public void downloadGeoIp() {
    	ConsoleLogger.info("LICENSE : This product includes GeoLite data created by MaxMind, available from http://www.maxmind.com");
    	File file = new File(getDataFolder(), "GeoIP.dat");
    	if (!file.exists()) {
        	try {
        		String url = "http://geolite.maxmind.com/download/geoip/database/GeoLiteCountry/GeoIP.dat.gz";
                URL downloadUrl = new URL(url);
                URLConnection conn = downloadUrl.openConnection();
                conn.setConnectTimeout(10000);
                conn.connect();
                InputStream input = conn.getInputStream();
                if (url.endsWith(".gz"))
                	input = new GZIPInputStream(input);
                OutputStream output = new FileOutputStream(file);
                byte[] buffer = new byte[2048];
                int length = input.read(buffer);
                while (length >= 0) {
                	output.write(buffer, 0, length);
                	length = input.read(buffer);
                }
                output.close();
                input.close();
        	} catch (Exception e) {}
    	}
    }

    public String getCountryCode(InetAddress ip) {
    	try {
    		if (ls == null)
    			ls = new LookupService(new File(getDataFolder(), "GeoIP.dat"));
        	String code = ls.getCountry(ip).getCode();
        	if (code != null && !code.isEmpty())
        		return code;
    	} catch (Exception e) {}
    	return null;
    }

    public String getCountryName(InetAddress ip) {
    	try {
    		if (ls == null)
    			ls = new LookupService(new File(getDataFolder(), "GeoIP.dat"));
        	String code = ls.getCountry(ip).getName();
        	if (code != null && !code.isEmpty())
        		return code;
    	} catch (Exception e) {}
    	return null;
    }

    public void switchAntiBotMod(boolean mode) {
    	this.antibotMod = mode;
    	Settings.switchAntiBotMod(mode);
    }

    private void recallEmail() {
    	if (!Settings.recallEmail)
    		return;
    	Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new Runnable(){
			@Override
			public void run() {
		    	for (Player player : Bukkit.getOnlinePlayers()) {
		    		if (player.isOnline()) {
		    			String name = player.getName().toLowerCase();
		    			if (database.isAuthAvailable(name))
		    				if (PlayerCache.getInstance().isAuthenticated(name)) {
		    					String email = database.getAuth(name).getEmail();
		    					if (email == null || email.isEmpty() || email.equalsIgnoreCase("your@email.com"))
		    						m._(player, "add_email");
		    				}
		    		}
		    	}
			}
    	}, 1, 1200 * Settings.delayRecall);
    }

    public String replaceAllInfos(String message, Player player) {
    	try {
        	message = message.replace("&", "\u00a7");
        	message = message.replace("{PLAYER}", player.getName());
        	message = message.replace("{ONLINE}", ""+this.getServer().getOnlinePlayers().length);
        	message = message.replace("{MAXPLAYERS}", ""+this.getServer().getMaxPlayers());
        	message = message.replace("{IP}", player.getAddress().getAddress().getHostAddress());
        	message = message.replace("{LOGINS}", ""+PlayerCache.getInstance().getLogged());
        	message = message.replace("{WORLD}", player.getWorld().getName());
        	message = message.replace("{SERVER}", this.getServer().getServerName());
        	message = message.replace("{VERSION}", this.getServer().getBukkitVersion());
        	message = message.replace("{COUNTRY}", this.getCountryName(player.getAddress().getAddress()));
    	} catch (Exception e) {}
    	return message;
    }
    
    public String getIP(Player player, String name) {
        String ip = player.getAddress().getAddress().getHostAddress();
        if (Settings.bungee) {
            if (realIp.containsKey(name))
                ip = realIp.get(name);
        }
        return ip;
    }

	public boolean isLoggedIp(String ip) {
		for (Player player : this.getServer().getOnlinePlayers()) {
			if(ip.equalsIgnoreCase(getIP(player, player.getName())) && database.isLogged(player.getName().toLowerCase()))
				return true;
		}
		return false;
	}

	public boolean hasJoinedIp(String ip) {
		for (Player player : this.getServer().getOnlinePlayers()) {
			if(ip.equalsIgnoreCase(getIP(player, player.getName())))
				return true;
		}
		return false;
	}
}
