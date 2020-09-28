package me.zombie_striker.sr;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPSClient;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.*;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class Main extends JavaPlugin {

	private static List<String> exceptions = new ArrayList<String>();
	private static String prefix = "&6[&3ServerRestorer&6]&8";
	private static String kickmessage = " Restoring server to previous save. Please rejoin in a few seconds.";
	BukkitTask br = null;
	private boolean saveTheConfig = false;
	private long lastSave = 0;
	private long timedist = 0;
	private File master = null;
	private File backups = null;
	private boolean saveServerJar = false;
	private boolean savePluiginJars = false;
	private boolean currentlySaving = false;
	private boolean automate = true;
	private boolean useFTP = false;
	private boolean useFTPS = false;
	private boolean useSFTP = false;
	private String serverFTP = "www.example.com";
	private String userFTP = "User";
	private String passwordFTP = "password";
	private int portFTP = 80;
	private String naming_format = "Backup-%date%";
	private SimpleDateFormat dateformat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
	private String removeFilePath = "";
	private long maxSaveSize = -1;
	private int maxSaveFiles = 1000;
	private boolean deleteZipOnFail = false;
	private boolean deleteZipOnFTP = false;

	private int hourToSaveAt = -1;

	private String separator = File.separator;



	private int compression = Deflater.BEST_COMPRESSION;

	public static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
		File destFile = new File(destinationDir, zipEntry.getName());

		String destDirPath = destinationDir.getCanonicalPath();
		String destFilePath = destFile.getCanonicalPath();

		if (!destFilePath.startsWith(destDirPath + File.separator)) {
			throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
		}

		return destFile;
	}

	private static boolean isExempt(String path) {
		path = path.toLowerCase().trim();
		for (String s : exceptions)
			if (path.endsWith(s.toLowerCase().trim()))
				return true;
		return false;
	}

	public static String humanReadableByteCount(long bytes, boolean si) {
		int unit = si ? 1000 : 1024;
		if (bytes < unit)
			return bytes + " B";
		int exp = (int) (Math.log(bytes) / Math.log(unit));
		String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
		return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
	}

	public static long folderSize(File directory) {
		long length = 0;
		if(directory==null)return -1;

		for (File file : directory.listFiles()) {
			if (file.isFile())
				length += file.length();
			else
				length += folderSize(file);
		}
		return length;
	}

	public static File firstFileModified(File dir) {
		File fl = dir;
		File[] files = fl.listFiles(new FileFilter() {
			public boolean accept(File file) {
				return file.isFile();
			}
		});
		long lastMod = Long.MAX_VALUE;
		File choice = null;
		for (File file : files) {
			if (file.lastModified() < lastMod) {
				choice = file;
				lastMod = file.lastModified();
			}
		}
		return choice;
	}

	public File getMasterFolder() {
		return master;
	}

	public File getBackupFolder() {
		return backups;
	}

	public long a(String path, long def) {
		if (getConfig().contains(path))
			return getConfig().getLong(path);
		saveTheConfig = true;
		getConfig().set(path, def);
		return def;
	}
	public Object a(String path, Object def) {
		if (getConfig().contains(path))
			return getConfig().get(path);
		saveTheConfig = true;
		getConfig().set(path, def);
		return def;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void onEnable() {
		master = getDataFolder().getAbsoluteFile().getParentFile().getParentFile();
		String path = ((String) a("getBackupFileDirectory", ""));
		backups = new File((path.isEmpty() ? master.getPath() : path) +  File.separator+"backups"+ File.separator);
		if (!backups.exists())
			backups.mkdirs();
		saveServerJar = (boolean) a("saveServerJar", false);
		savePluiginJars = (boolean) a("savePluginJars", false);

		timedist = toTime((String) a("AutosaveDelay", "1D,0H"));
		lastSave = a("LastAutosave", 0L);

		automate = (boolean) a("enableautoSaving", true);

		naming_format = (String) a("FileNameFormat", naming_format);

		String unPrefix = (String) a("prefix", "&6[&3ServerRestorer&6]&8");
		prefix = ChatColor.translateAlternateColorCodes('&', unPrefix);
		String kicky = (String) a("kickMessage", unPrefix + " Restoring server to previous save. Please rejoin in a few seconds.");
		kickmessage = ChatColor.translateAlternateColorCodes('&', kicky);

		useFTP = (boolean) a("EnableFTP", false);
		useFTPS = (boolean) a("EnableFTPS", false);
		useSFTP = (boolean) a("EnableSFTP", false);
		serverFTP = (String) a("FTPAddress", serverFTP);
		portFTP = (int) a("FTPPort", portFTP);
		userFTP = (String) a("FTPUsername", userFTP);
		passwordFTP = (String) a("FTPPassword", passwordFTP);


		compression = (int) a("CompressionLevel_Max_9", compression);

		removeFilePath = (String) a("FTP_Directory", removeFilePath);

		hourToSaveAt = (int) a("AutoBackup-HourToBackup", hourToSaveAt);

		if (!getConfig().contains("exceptions")) {
			exceptions.add("logs");
			exceptions.add("crash-reports");
			exceptions.add("backups");
			exceptions.add("dynmap");
			exceptions.add(".lock");
			exceptions.add("pixelprinter");
		}
		exceptions = (List<String>) a("exceptions", exceptions);

		maxSaveSize = toByteSize((String) a("MaxSaveSize", "10G"));
		maxSaveFiles = (int) a("MaxFileSaved", 1000);

		deleteZipOnFTP = (boolean) a("DeleteZipOnFTPTransfer", false);
		deleteZipOnFail = (boolean) a("DeleteZipIfFailed", false);
		separator = (String) a("FolderSeparator", separator);
		if (saveTheConfig)
			saveConfig();
		if (automate) {
			final JavaPlugin thi = this;
			br = new BukkitRunnable() {
				@Override
				public void run() {
					Calendar calendar = GregorianCalendar.getInstance(); // creates a new calendar instance
					calendar.setTime(new Date());   // assigns calendar to given date
					int hour = calendar.get(Calendar.HOUR_OF_DAY);

					if (System.currentTimeMillis() - lastSave >= timedist && (hourToSaveAt==-1 || hourToSaveAt == hour)) {
						new BukkitRunnable() {
							@Override
							public void run() {
								getConfig().set("LastAutosave", lastSave = (System.currentTimeMillis()-5000));
								save(Bukkit.getConsoleSender());
								saveConfig();
							}
						}.runTaskLater(thi, 0);
						return;
					}
				}
			}.runTaskTimerAsynchronously(this, 20, 20*60);
		}

		new Metrics(this);

		/*if (Bukkit.getPluginManager().getPlugin("PluginConstructorAPI") == null)
			GithubDependDownloader.autoUpdate(this,
					new File(getDataFolder().getParentFile(), "PluginConstructorAPI.jar"), "ZombieStriker",
					"PluginConstructorAPI", "PluginConstructorAPI.jar");*/

		//new Updater(this, 280536);

	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

		if (args.length == 1) {
			List<String> list = new ArrayList<>();
			String[] commands = new String[]{"disableAutoSaver", "enableAutoSaver", "restore", "save","stop", "toggleOptions"};
			for (String f : commands) {
				if (f.toLowerCase().startsWith(args[0].toLowerCase()))
					list.add(f);
			}
			return list;

		}

		if (args.length > 1) {
			if (args[0].equalsIgnoreCase("restore")) {
				List<String> list = new ArrayList<>();
				for (File f : getBackupFolder().listFiles()) {
					if (f.getName().toLowerCase().startsWith(args[1].toLowerCase()))
						list.add(f.getName());
				}
				return list;
			}
		}
		return super.onTabComplete(sender, command, alias, args);
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!sender.hasPermission("serverrestorer.command")) {
			sender.sendMessage(prefix + ChatColor.RED + " You do not have permission to use this command.");
			return true;
		}
		if (args.length == 0) {
			sender.sendMessage(ChatColor.GOLD + "---===+Server Restorer+===---");
			sender.sendMessage("/sr save : Saves the server");
			sender.sendMessage("/sr stop : Stops creating a backup of the server");
			sender.sendMessage("/sr restore <backup> : Restores server to previous backup (automatically restarts)");
			sender.sendMessage("/sr enableAutoSaver [1H,6H,1D,7D] : Configure how long it takes to autosave");
			sender.sendMessage("/sr disableAutoSaver : Disables the autosaver");
			sender.sendMessage("/sr toggleOptions : TBD");
			return true;
		}
		if (args[0].equalsIgnoreCase("restore")) {
			if(true) {
				sender.sendMessage(prefix+ "Restore feature is temporarily disabled. Please load the files manually.");
			return true;
			}
			if (!sender.hasPermission("serverrestorer.restore")) {
				sender.sendMessage(prefix + ChatColor.RED + " You do not have permission to use this command.");
				return true;
			}
			if (currentlySaving) {
				sender.sendMessage(prefix + " The server is currently being saved. Please wait.");
				return true;
			}
			if (args.length < 2) {
				sender.sendMessage(prefix + " A valid backup file is required.");
				return true;
			}
			File backup = new File(getBackupFolder(), args[1]);
			if (!backup.exists()) {
				sender.sendMessage(prefix + " The file \"" + args[1] + "\" does not exist.");
				return true;
			}
			restore(backup);
			sender.sendMessage(prefix + " Restoration complete.");
			return true;
		}

		if (args[0].equalsIgnoreCase("stop")) {
			if (!sender.hasPermission("serverrestorer.save")) {
				sender.sendMessage(prefix + ChatColor.RED + " You do not have permission to use this command.");
				return true;
			}
			if (currentlySaving) {
				currentlySaving=false;
				return true;
			}
			sender.sendMessage(prefix + " The server is not currently being saved.");
			return true;
		}
		if (args[0].equalsIgnoreCase("save")) {
			if (!sender.hasPermission("serverrestorer.save")) {
				sender.sendMessage(prefix + ChatColor.RED + " You do not have permission to use this command.");
				return true;
			}
			if (currentlySaving) {
				sender.sendMessage(prefix + " The server is currently being saved. Please wait.");
				return true;
			}
			save(sender);
			return true;
		}
		if (args[0].equalsIgnoreCase("disableAutoSaver")) {
			if (!sender.hasPermission("serverrestorer.save")) {
				sender.sendMessage(prefix + ChatColor.RED + " You do not have permission to use this command.");
				return true;
			}
			if (br != null)
				br.cancel();
			br = null;
			getConfig().set("enableautoSaving", false);
			saveConfig();
			sender.sendMessage(prefix + " Canceled delay.");
		}
		if (args[0].equalsIgnoreCase("enableAutoSaver")) {
			if (!sender.hasPermission("serverrestorer.save")) {
				sender.sendMessage(prefix + ChatColor.RED + " You do not have permission to use this command.");
				return true;
			}
			if (args.length == 1) {
				sender.sendMessage(prefix + " Please select a delay [E.G. 0.5H, 6H, 1D, 7D...]");
				return true;
			}
			String delay = args[1];
			getConfig().set("AutosaveDelay", delay);
			getConfig().set("enableautoSaving", true);
			saveConfig();
			if (br != null)
				br.cancel();
			br = null;
			br = new BukkitRunnable() {
				@Override
				public void run() {
					if (System.currentTimeMillis() - lastSave > timedist) {
						save(Bukkit.getConsoleSender());
						getConfig().set("LastAutosave", lastSave = System.currentTimeMillis());
						saveConfig();
						return;
					}
				}
			}.runTaskTimerAsynchronously(this, 20, 20 * 60 * 30);

			sender.sendMessage(prefix + " Set the delay to \"" + delay + "\".");
		}
		if (args[0].equalsIgnoreCase("toggleOptions")) {
			if (!sender.hasPermission("serverrestorer.save")) {
				sender.sendMessage(prefix + ChatColor.RED + " You do not have permission to use this command.");
				return true;
			}
			sender.sendMessage(prefix + " Coming soon !");
			return true;
		}
		return true;
	}

	public void save(CommandSender sender) {
		currentlySaving = true;
		sender.sendMessage(prefix + " Starting to save directory. Please wait.");
		List<World> autosave = new ArrayList<>();
		for (World loaded : Bukkit.getWorlds()) {
			try {
				loaded.save();
				if (loaded.isAutoSave()) {
					autosave.add(loaded);
					loaded.setAutoSave(false);
				}

			} catch (Exception e) {
			}
		}
		new BukkitRunnable() {
			@Override
			public void run() {
				try {
					try {
						if(backups.listFiles().length > maxSaveFiles){
							for(int i  = 0; i < backups.listFiles().length-maxSaveFiles; i++){
								File oldestBack = firstFileModified(backups);
								sender.sendMessage(prefix + ChatColor.RED + oldestBack.getName()
										+ ": File goes over max amount of files that can be saved.");
								oldestBack.delete();
							}
						}
						for (int j = 0; j < Math.min(maxSaveFiles, backups.listFiles().length - 1); j++) {
							if (folderSize(backups) >= maxSaveSize) {
								File oldestBack = firstFileModified(backups);
								sender.sendMessage(prefix + ChatColor.RED + oldestBack.getName()
										+ ": The current save goes over the max savesize, and so the oldest file has been deleted. If you wish to save older backups, copy them to another location.");
								oldestBack.delete();
							} else {
								break;
							}
						}
					} catch (Error | Exception e) {
					}
					final long time = lastSave = System.currentTimeMillis();
					Date d = new Date(lastSave);
					File zipFile = new File(getBackupFolder(),
							naming_format.replaceAll("%date%", dateformat.format(d)) + ".zip");
					if (!zipFile.exists()) {
						zipFile.getParentFile().mkdirs();
						zipFile = new File(getBackupFolder(),
								naming_format.replaceAll("%date%", dateformat.format(d)) + ".zip");
						zipFile.createNewFile();
					}
					zipFolder(getMasterFolder().getPath(), zipFile.getPath());

					long timeDif = (System.currentTimeMillis() - time) / 1000;
					String timeDifS = (((int) (timeDif / 60)) + "M, " + (timeDif % 60) + "S");

					if(!currentlySaving){
						for (World world : autosave)
							world.setAutoSave(true);
						sender.sendMessage(prefix + " Backup canceled.");
						cancel();
						return;
					}

					sender.sendMessage(prefix + " Done! Backup took:" + timeDifS);
					File tempBackupCheck = new File(getMasterFolder(), "backups");
					sender.sendMessage(prefix + " Compressed server with size of "
							+ (humanReadableByteCount(folderSize(getMasterFolder())
							- (tempBackupCheck.exists() ? folderSize(tempBackupCheck) : 0), false))
							+ " to " + humanReadableByteCount(zipFile.length(), false));
					currentlySaving = false;
					for (World world : autosave)
						world.setAutoSave(true);
					if (useSFTP) {
						try {
							sender.sendMessage(prefix + " Starting SFTP Transfer");
							JSch jsch = new JSch();
							Session session = jsch.getSession(userFTP, serverFTP, portFTP);
							session.setConfig("PreferredAuthentications", "password");
							session.setPassword(passwordFTP);
							session.connect(1000 * 20);
							Channel channel = session.openChannel("sftp");
							ChannelSftp sftp = (ChannelSftp) channel;
							sftp.connect(1000 * 20);
						} catch (Exception | Error e) {
							sender.sendMessage(
									prefix + " FAILED TO SFTP TRANSFER FILE: " + zipFile.getName() + ". ERROR IN CONSOLE.");
							if (deleteZipOnFail)
								zipFile.delete();
							e.printStackTrace();
						}
					} else if (useFTPS) {
						sender.sendMessage(prefix + " Starting FTPS Transfer");
						FileInputStream zipFileStream = new FileInputStream(zipFile);
						FTPSClient ftpClient = new FTPSClient();
						try {
							if (ftpClient.isConnected()) {
								sender.sendMessage(prefix + "FTPSClient was already connected. Disconnecting");
								ftpClient.logout();
								ftpClient.disconnect();
								ftpClient = new FTPSClient();
							}
							sendFTP(sender, zipFile, ftpClient, zipFileStream, removeFilePath);
							if (deleteZipOnFTP)
								zipFile.delete();
						} catch (Exception | Error e) {
							sender.sendMessage(
									prefix + " FAILED TO FTPS TRANSFER FILE: " + zipFile.getName() + ". ERROR IN CONSOLE.");
							if (deleteZipOnFail)
								zipFile.delete();
							e.printStackTrace();
						} finally {
							try {
								if (ftpClient.isConnected()) {
									sender.sendMessage(prefix + "Disconnecting");
									ftpClient.logout();
									ftpClient.disconnect();
								}
							} catch (IOException ex) {
								ex.printStackTrace();
							}
						}
					} else if (useFTP) {
						sender.sendMessage(prefix + " Starting FTP Transfer");
						FileInputStream zipFileStream = new FileInputStream(zipFile);
						FTPClient ftpClient = new FTPClient();
						try {
							if (ftpClient.isConnected()) {
								sender.sendMessage(prefix + "FTPClient was already connected. Disconnecting");
								ftpClient.logout();
								ftpClient.disconnect();
								ftpClient = new FTPClient();
							}
							sendFTP(sender, zipFile, ftpClient, zipFileStream, removeFilePath);
							if (deleteZipOnFTP)
								zipFile.delete();
						} catch (Exception | Error e) {
							sender.sendMessage(
									prefix + " FAILED TO FTP TRANSFER FILE: " + zipFile.getName() + ". ERROR IN CONSOLE.");
							if (deleteZipOnFail)
								zipFile.delete();
							e.printStackTrace();
						} finally {
							try {
								if (ftpClient.isConnected()) {
									sender.sendMessage(prefix + "Disconnecting");
									ftpClient.logout();
									ftpClient.disconnect();
								}
							} catch (IOException ex) {
								ex.printStackTrace();
							}
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}.runTaskAsynchronously(this);
	}

	public void sendFTP(CommandSender sender, File zipFile, FTPClient ftpClient, FileInputStream zipFileStream, String path)
			throws SocketException, IOException {
		ftpClient.connect(serverFTP, portFTP);
		ftpClient.login(userFTP, passwordFTP);
		ftpClient.enterLocalPassiveMode();

		ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

		boolean done = ftpClient.storeFile(path + zipFile.getName(), zipFileStream);
		zipFileStream.close();
		if (done) {
			sender.sendMessage(prefix + " Transfered backup using FTP!");
		} else {
			sender.sendMessage(prefix + " Something failed (maybe)! Status=" + ftpClient.getStatus());
		}

	}

	public long toTime(String time) {
		long militime = 0;
		for(String split : time.split(",")) {
			split = split.trim();
			long k = 1;
			if (split.toUpperCase().endsWith("H")) {
				k *= 60 * 60;
			} else if (split.toUpperCase().endsWith("D")) {
				k *= 60 * 60 * 24;
			} else {
				k *= 60 * 60 * 24;
			}
			double j = Double.parseDouble(split.substring(0, split.length() - 1));
			militime += (j*k);
		}
		militime *= 1000;
		return militime;
	}

	public void restore(File backup) {

		//Kick all players
		for (Player player : Bukkit.getOnlinePlayers())
			player.kickPlayer(kickmessage);

		//Disable all plugins safely.
		for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
			if (p != this) {
				try {
					Bukkit.getPluginManager().disablePlugin(p);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		//Unload all worlds.
		List<String> names = new ArrayList<>();
		for (World w : Bukkit.getWorlds()) {
			for (Chunk c : w.getLoadedChunks()) {
				c.unload(false);
			}
			names.add(w.getName());
			Bukkit.unloadWorld(w, true);
		}
		for(String worldnames : names){
			File worldFile = new File(getMasterFolder(),worldnames);
			if(worldFile.exists())
				worldFile.delete();
		}

		//Start overriding files.
		File parentTo = getMasterFolder().getParentFile();
		try {
			byte[] buffer = new byte[1024];
			ZipInputStream zis = new ZipInputStream(new FileInputStream(backup));
			ZipEntry zipEntry = zis.getNextEntry();
			while (zipEntry != null) {
				try {
					File newFile = newFile(parentTo, zipEntry);
					FileOutputStream fos = new FileOutputStream(newFile);
					int len;
					while ((len = zis.read(buffer)) > 0) {
						fos.write(buffer, 0, len);
					}
					fos.close();
					zipEntry = zis.getNextEntry();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			zis.closeEntry();
			zis.close();
		} catch (Exception e4) {
			e4.printStackTrace();
		}
		Bukkit.shutdown();
	}

	public void zipFolder(String srcFolder, String destZipFile) throws Exception {
		ZipOutputStream zip = null;
		FileOutputStream fileWriter = null;

		fileWriter = new FileOutputStream(destZipFile);
		zip = new ZipOutputStream(fileWriter);


		zip.setLevel(compression);

		addFolderToZip("", srcFolder, zip);
		zip.flush();
		zip.close();
	}

	private void addFileToZip(String path, String srcFile, ZipOutputStream zip) {
		try {
			File folder = new File(srcFile);
			if (!isExempt(srcFile)) {

				if(!currentlySaving)
					return;
				// this.savedBytes += folder.length();
				if (folder.isDirectory()) {
					addFolderToZip(path, srcFile, zip);
				} else {
					if (folder.getName().endsWith("jar")) {
						if (path.contains("plugins") && (!savePluiginJars) || (!path.contains("plugins") && (!saveServerJar))) {
							return;
						}
					}

					byte[] buf = new byte['?'];

					FileInputStream in = new FileInputStream(srcFile);
					zip.putNextEntry(new ZipEntry(path + separator + folder.getName()));
					int len;
					while ((len = in.read(buf)) > 0) {
						zip.write(buf, 0, len);
					}
					in.close();
				}
			}
		}catch (FileNotFoundException e4){
			Bukkit.getConsoleSender().sendMessage(prefix + " FAILED TO ZIP FILE: " + srcFile+" Reason: "+e4.getClass().getName());
			e4.printStackTrace();
		}catch (IOException e5){
			if(!srcFile.endsWith(".db")) {
				Bukkit.getConsoleSender().sendMessage(prefix + " FAILED TO ZIP FILE: " + srcFile + " Reason: " + e5.getClass().getName());
				e5.printStackTrace();
			}else{
				Bukkit.getConsoleSender().sendMessage(prefix + " Skipping file " + srcFile +" due to another process that has locked a portion of the file");
			}

		}
	}

	private void addFolderToZip(String path, String srcFolder, ZipOutputStream zip) {
		if ((!path.toLowerCase().contains("backups")) && (!isExempt(path))) {
			try {
				File folder = new File(srcFolder);
				String[] arrayOfString;
				int j = (arrayOfString = folder.list()).length;
				for (int i = 0; i < j; i++) {
					if(!currentlySaving)
						break;
					String fileName = arrayOfString[i];
					if (path.equals("")) {
						addFileToZip(folder.getName(), srcFolder + separator + fileName, zip);
					} else {
						addFileToZip(path + separator + folder.getName(), srcFolder +  separator + fileName, zip);
					}
				}
			} catch (Exception e) {
			}
		}
	}

	private long toByteSize(String s) {
		long k = Long.parseLong(s.substring(0, s.length() - 1));
		if (s.toUpperCase().endsWith("G")) {
			k *= 1000 * 1000 * 1000;
		} else if (s.toUpperCase().endsWith("M")) {
			k *= 1000 * 1000;
		} else if (s.toUpperCase().endsWith("K")) {
			k *= 1000;
		} else {
			k *= 10;
		}
		return k;
	}
}
