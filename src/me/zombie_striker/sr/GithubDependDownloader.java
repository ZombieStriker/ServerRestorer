package me.zombie_striker.sr;

import java.io.*;
import java.net.*;

import com.google.gson.*;
import org.bukkit.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

public class GithubDependDownloader {

	

	public static boolean autoUpdate(final Plugin main, final File output, String author, String githubProject,
			String jarname) {
		try {

			String tagname = null;
			URL api = new URL("https://api.github.com/repos/" + author + "/" + githubProject + "/releases/latest");
			URLConnection con = api.openConnection();
			con.setConnectTimeout(15000);
			con.setReadTimeout(15000);

			JsonObject json = new JsonParser().parse(new InputStreamReader(con.getInputStream())).getAsJsonObject();
			tagname = json.get("tag_name").getAsString();

			final URL download = new URL("https://github.com/" + author + "/" + githubProject + "/releases/download/"
					+ tagname + "/" + jarname);

			Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "Found a new version " + ChatColor.WHITE + tagname
					+ ChatColor.LIGHT_PURPLE + " downloading now!!");

			new BukkitRunnable() {

				@Override
				public void run() {
					try {

						InputStream in = download.openStream();

						File pluginFile = output;

						// File temp = new File("plugins/update");
						// if (!temp.exists()) {
						// temp.mkdir();
						// }

						// Path path = new File("plugins/update" + File.separator + "COD.jar").toPath();
						pluginFile.setWritable(true, false);
						pluginFile.delete();
						// Files.copy(in, pluginFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
						copy(in, new FileOutputStream(pluginFile));

						new BukkitRunnable() {
							public void run() {
								Bukkit.reload();
							}
						}.runTaskLater(main, 4);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}.runTaskLaterAsynchronously(main, 0);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}

	private static long copy(InputStream in, OutputStream out) throws IOException {
		long bytes = 0;
		byte[] buf = new byte[0x1000];
		while (true) {
			int r = in.read(buf);
			if (r == -1)
				break;
			out.write(buf, 0, r);
			bytes += r;
			// debug("Another 4K, current: " + r);
		}
		out.flush();
		out.close();
		in.close();
		return bytes;
	}

}
