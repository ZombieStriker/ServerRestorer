package me.zombie_striker.sr;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPSClient;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class Main extends JavaPlugin {

	private boolean saveTheConfig = false;

	private long lastSave = 0;
	private long timedist = 0;

	private File master = null;
	private File backups = null;
	private boolean saveServerJar = false;

	private boolean automate = true;

	private boolean useFTP = false;
	private boolean useSFTP = false;
	private String serverFTP = "www.example.com";
	private String userFTP = "User";
	private String passwordFTP = "password";
	private int portFTP = 80;

	private String naming_format = "Backup-%date%";
	private SimpleDateFormat dateformat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");

	private String removeFilePath = "";

	private long maxSaveSize = -1;

	private boolean deleteZipOnFail = false;

	BukkitTask br = null;

	private boolean deleteZipOnFTP = false;

	private static List<String> exceptions = new ArrayList<String>();

	private static String prefix = ChatColor.GOLD + "[" + ChatColor.AQUA + "ServerRestorer" + ChatColor.GOLD + "]"
			+ ChatColor.GRAY;

	public File getMasterFolder() {
		return master;
	}

	public File getBackupFolder() {
		return backups;
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
		backups = new File((path.isEmpty() ? master.getPath() : path) + "/backups/");
		if (!backups.exists())
			backups.mkdirs();
		saveServerJar = (boolean) a("saveServerJar", false);
		if (getConfig().contains("Autosave") && !getConfig().contains("AutosaveDelay")) {
			timedist = toTime((String) a("Autosave", "1D"));
		} else {
			timedist = toTime((String) a("AutosaveDelay", "1D"));
		}
		try {
			lastSave = (long) a("LastAutosave", 0L);
		} catch (Error | Exception e4) {
			lastSave = new Long((Integer) a("LastAutosave", 0L));
		}
		automate = (boolean) a("enableautoSaving", true);

		naming_format = (String) a("FileNameFormat", naming_format);

		useFTP = (boolean) a("EnableFTP", false);
		useFTP = (boolean) a("EnableSFTP", false);
		serverFTP = (String) a("FTPAdress", serverFTP);
		portFTP = (int) a("FTPPort", portFTP);
		userFTP = (String) a("FTPUsername", userFTP);
		passwordFTP = (String) a("FTPPassword", passwordFTP);

		removeFilePath = (String) a("FTP_Directory", removeFilePath);

		if (!getConfig().contains("exceptions")) {
			exceptions.add("logs");
			exceptions.add("crash-reports");
			exceptions.add("backups");
		}
		exceptions = (List<String>) a("exceptions", exceptions);

		maxSaveSize = toByteSize((String) a("MaxSaveSize", "10G"));

		deleteZipOnFTP = (boolean) a("DeleteZipOnFTPTransfer", false);
		deleteZipOnFail = (boolean) a("DeleteZipIfFailed", false);
		if (saveTheConfig)
			saveConfig();
		if (automate)
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

		new Metrics(this);

		if (Bukkit.getPluginManager().getPlugin("PluginConstructorAPI") == null)
			// new DependencyDownloader(this, 276723);
			GithubDependDownloader.autoUpdate(this,
					new File(getDataFolder().getParentFile(), "PluginConstructorAPI.jar"), "ZombieStriker",
					"PluginConstructorAPI", "PluginConstructorAPI.jar");

		new Updater(this, 280536);

	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length == 0) {
			sender.sendMessage(ChatColor.GOLD + "---===+Server Restorer+===---");
			sender.sendMessage("/sr save : Saves the server");
			sender.sendMessage("/sr enableAutoSaver [1H,6H,1D,7D] : Configure how long it takes to autosave");
			sender.sendMessage("/sr disableAutoSaver : Disables the autosaver");
			sender.sendMessage("/sr toggleOptions : TBD");
			return true;
		}
		if (args[0].equalsIgnoreCase("save")) {
			if (!sender.hasPermission("serverrestorer.save")) {
				sender.sendMessage(prefix + ChatColor.RED + " You do not have permission to use this command.");
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
			sender.sendMessage(prefix + " Comeing soon !");
			return true;
		}
		return true;
	}

	public void save(CommandSender sender) {
		sender.sendMessage(prefix + " Starting to save directory. Please wait.");
		for (World loaded : Bukkit.getWorlds()) {
			try {
				loaded.save();
			} catch (Exception e) {
			}
		}
		new BukkitRunnable() {
			@Override
			public void run() {
				try {
					try {
						for (int j = 0; j < Math.min(10, backups.listFiles().length - 1); j++) {
							if (folderSize(backups) > maxSaveSize) {
								File oldestBack = lastFileModified(backups);
								sender.sendMessage(prefix + ChatColor.RED + oldestBack.getName()
										+ ": This save goes over the max savesize, and has just deleted the oldest file. If you wish to save older backups, copy them to another location.");
								oldestBack.delete();
							} else {
								break;
							}
						}
					} catch (Error | Exception e) {
					}
					final long time = lastSave = System.currentTimeMillis();
					Date d = new Date(System.currentTimeMillis());
					File ff = new File(getBackupFolder(),
							naming_format.replaceAll("%date%", dateformat.format(d)) + ".zip");
					if (!ff.exists())
						ff.createNewFile();
					zipFolder(getMasterFolder().getPath(), ff.getPath());
					long timeDif = (System.currentTimeMillis() - time) / 1000;
					String timeDifS = (((int) (timeDif / 60)) + "M, " + (timeDif % 60) + "S");
					sender.sendMessage(prefix + " Done! Packing took:" + timeDifS);
					File tempBackupCheck = new File(getMasterFolder(), "backups");
					sender.sendMessage(prefix + " Compressed server with size of "
							+ (humanReadableByteCount(folderSize(getMasterFolder())
									- (tempBackupCheck.exists() ? folderSize(tempBackupCheck) : 0), false))
							+ " to " + humanReadableByteCount(ff.length(), false));
					if (useSFTP) {
						sender.sendMessage(prefix + " Starting SFTP Transfer");
						FileInputStream zipFileStream = new FileInputStream(ff);
						FTPSClient ftpClient = new FTPSClient();
						try {
							if (ftpClient.isConnected()) {
								sender.sendMessage(prefix + "FTPSClient was already connected. Disconnecting");
								ftpClient.logout();
								ftpClient.disconnect();
								ftpClient = new FTPSClient();
							}
							sendFTP(sender, ff, ftpClient, zipFileStream, removeFilePath);
							if (deleteZipOnFTP)
								ff.delete();
						} catch (Exception | Error e) {
							sender.sendMessage(
									prefix + " FAILED TO FTP TRANSFER FILE: " + ff.getName() + ". ERROR IN CONSOLE.");
							if (deleteZipOnFail)
								ff.delete();
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
						FileInputStream zipFileStream = new FileInputStream(ff);
						FTPClient ftpClient = new FTPClient();
						try {
								if (ftpClient.isConnected()) {
									sender.sendMessage(prefix + "FTPClient was already connected. Disconnecting");
									ftpClient.logout();
									ftpClient.disconnect();
									ftpClient = new FTPClient();
								}
								sendFTP(sender, ff, ftpClient, zipFileStream, removeFilePath);
								if (deleteZipOnFTP)
									ff.delete();
						} catch (Exception | Error e) {
							sender.sendMessage(
									prefix + " FAILED TO FTP TRANSFER FILE: " + ff.getName() + ". ERROR IN CONSOLE.");
							if (deleteZipOnFail)
								ff.delete();
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

	public void sendFTP(CommandSender sender, File ff, FTPClient ftpClient, FileInputStream zipFileStream, String path)
			throws SocketException, IOException {
		ftpClient.connect(serverFTP, portFTP);
		ftpClient.login(userFTP, passwordFTP);
		ftpClient.enterLocalPassiveMode();

		ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

		boolean done = ftpClient.storeFile(path + ff.getName(), zipFileStream);
		zipFileStream.close();
		if (done) {
			sender.sendMessage(prefix + " Transfered backup using FTP!");
		} else {
			sender.sendMessage(prefix + " Something failed (maybe)! Status=" + ftpClient.getStatus());
		}

	}

	public long toTime(String time) {
		long k = 1000;
		if (time.toUpperCase().endsWith("H")) {
			k *= 60 * 60;
		} else if (time.toUpperCase().endsWith("D")) {
			k *= 60 * 60 * 24;
		} else {
			k *= 60 * 60 * 24;
		}
		double j = Double.parseDouble(time.substring(0, time.length() - 1));
		return (int) (j * k);
	}

	public void zipFolder(String srcFolder, String destZipFile) throws Exception {
		ZipOutputStream zip = null;
		FileOutputStream fileWriter = null;

		fileWriter = new FileOutputStream(destZipFile);
		zip = new ZipOutputStream(fileWriter);

		addFolderToZip("", srcFolder, zip);
		zip.flush();
		zip.close();
	}

	private void addFileToZip(String path, String srcFile, ZipOutputStream zip) {
		try {
			if ((!isExempt(path))) {
				File folder = new File(srcFile);

				// this.savedBytes += folder.length();
				if (folder.isDirectory()) {
					addFolderToZip(path, srcFile, zip);
				} else {
					if (!saveServerJar)
						if (folder.getName().endsWith("jar")) {
							if (folder.getName().toLowerCase().startsWith("spigot")
									|| folder.getName().toLowerCase().startsWith("craftbukkit")
									|| folder.getName().toLowerCase().startsWith("bukkit")) {
								return;
								// Don't save jar files
							}
						}

					byte[] buf = new byte['?'];

					FileInputStream in = new FileInputStream(srcFile);
					zip.putNextEntry(new ZipEntry(path + "/" + folder.getName()));
					int len;
					while ((len = in.read(buf)) > 0) {
						zip.write(buf, 0, len);
					}
					in.close();
				}
			}
		} catch (Exception e) {
			Bukkit.getConsoleSender().sendMessage(prefix + " FAILED TO ZIP FILE: " + srcFile);
		}
	}

	private void addFolderToZip(String path, String srcFolder, ZipOutputStream zip) {
		if ((!path.toLowerCase().contains("backups")) && (!isExempt(path))) {
			// if (main.getConfiguration().getBoolean("debug")) {
			// }
			try {
				File folder = new File(srcFolder);
				// this.savedBytes += folder.length();
				// Backup.updatePercent(this.size, this.savedBytes);
				String[] arrayOfString;
				int j = (arrayOfString = folder.list()).length;
				for (int i = 0; i < j; i++) {
					String fileName = arrayOfString[i];
					if (path.equals("")) {
						addFileToZip(folder.getName(), srcFolder + "/" + fileName, zip);
					} else {
						addFileToZip(path + "/" + folder.getName(), srcFolder + "/" + fileName, zip);
					}
				}
			} catch (Exception e) {
			}
		}
	}

	private static boolean isExempt(String path) {
		path = path.toLowerCase();
		for (String s : exceptions)
			if (path.endsWith(s))
				return true;
		return false;
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
		for (File file : directory.listFiles()) {
			if (file.isFile())
				length += file.length();
			else
				length += folderSize(file);
		}
		return length;
	}

	public static File lastFileModified(File dir) {
		File fl = dir;
		File[] files = fl.listFiles(new FileFilter() {
			public boolean accept(File file) {
				return file.isFile();
			}
		});
		long lastMod = Long.MIN_VALUE;
		File choice = null;
		for (File file : files) {
			if (file.lastModified() > lastMod) {
				choice = file;
				lastMod = file.lastModified();
			}
		}
		return choice;
	}
}
