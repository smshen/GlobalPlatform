package openkms.gpj;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Vector;

import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CardTerminals;
import javax.smartcardio.TerminalFactory;

public class GPJTool {

	public static void main(String[] args) throws IOException {

		final class InstallEntry {
			AID appletAID;
			AID packageAID;
			int priv;
			byte[] params;
		}

		boolean listApplets = false;
		int keySet = 0;
		byte[][] keys = { GlobalPlatform.defaultEncKey, GlobalPlatform.defaultMacKey, GlobalPlatform.defaultKekKey };
		AID sdAID = null;
		int diver = GlobalPlatform.DIVER_NONE;
		Vector<AID> deleteAID = new Vector<AID>();
		boolean deleteDeps = false;

		URL capFileUrl = null;
		int loadSize = GlobalPlatform.defaultLoadSize;
		boolean loadCompSep = false;
		boolean loadDebug = false;
		boolean loadParam = false;
		boolean useHash = false;
		boolean verbose = false;
		boolean format = false;
		boolean listReaders = false;
		
		int apduMode = GlobalPlatform.APDU_CLR;

		Vector<InstallEntry> installs = new Vector<InstallEntry>();
		
		try {				
			for (int i = 0; i < args.length; i++) {

				if (args[i].equals("-h") || args[i].equals("-help") || args[i].equals("--help")) {
					usage();
					System.exit(0);
				}

				// All other options.
				if (args[i].equals("-v") || args[i].equals("-verbose") || args[i].equals("-debug")) {
					verbose = true;
				} else if (args[i].equals("-readers")) {
					listReaders = true;
				} else if (args[i].equals("-list")) {
					listApplets = true;
				} else if (args[i].equals("-keyset")) {
					i++;
					keySet = Integer.parseInt(args[i]);
					if (keySet <= 0 || keySet > 127) {
						throw new IllegalArgumentException("Key set number " + keySet + " out of range.");
					}
				} else if (args[i].equals("-sdaid")) {
					i++;
					byte[] aid = GPUtils.stringToByteArray(args[i]);
					if (aid == null) {
						aid = GPUtils.readableStringToByteArray(args[i]);
					}
					if (aid == null) {
						throw new IllegalArgumentException("Malformed SD AID: " + args[i]);
					}
					sdAID = new AID(aid);
					if (AID.SD_AIDS.get(AID.GEMALTO).equals(sdAID)) {
						byte[] gemMotherKey = GlobalPlatform.SPECIAL_MOTHER_KEYS.get(AID.GEMALTO);
						keys = new byte[][] { gemMotherKey, gemMotherKey, gemMotherKey };
						diver = GlobalPlatform.DIVER_VISA2;
					}
				} else if (args[i].equals("-visa2")) {
					diver = GlobalPlatform.DIVER_VISA2;
				} else if (args[i].equals("-emv")) {
					diver = GlobalPlatform.DIVER_EMV;
				} else if (args[i].equals("-mode")) {
					i++;
					// TODO: RMAC modes
					if ("CLR".equals(args[i])) {
						apduMode = GlobalPlatform.APDU_CLR;
					} else if ("MAC".equals(args[i])) {
						apduMode = GlobalPlatform.APDU_MAC;
					} else if ("ENC".equals(args[i])) {
						apduMode = GlobalPlatform.APDU_ENC;
					} else {
						throw new IllegalArgumentException("Invalid APDU mode: " + args[i]);
					}
				} else if (args[i].equals("-delete")) {
					i++;
					byte[] aid = GPUtils.stringToByteArray(args[i]);
					if (aid == null) {
						aid = GPUtils.readableStringToByteArray(args[i]);
					}
					if (aid == null) {
						throw new IllegalArgumentException("Malformed AID: " + args[i]);
					}
					deleteAID.add(new AID(aid));
				} else if (args[i].equals("-deletedeps")) {
					deleteDeps = true;
				} else if (args[i].equals("-format")) {
					format = true;
				} else if (args[i].equals("-loadsize")) {
					i++;
					loadSize = Integer.parseInt(args[i]);
					if (loadSize <= 16 || loadSize > 255) {
						throw new IllegalArgumentException("Load size " + loadSize + " out of range.");
					}
				} else if (args[i].equals("-loadsep")) {
					loadCompSep = true;
				} else if (args[i].equals("-loaddebug")) {
					loadDebug = true;
				} else if (args[i].equals("-loadparam")) {
					loadParam = true;
				} else if (args[i].equals("-loadhash")) {
					useHash = true;
				} else if (args[i].equals("-load")) {
					i++;
					try {
						capFileUrl = new URL(args[i]);
					} catch (MalformedURLException e) {
						// Try with "file:" prepended
						capFileUrl = new URL("file:" + args[i]);
					}
					try {
						InputStream in = capFileUrl.openStream();
						in.close();
					} catch (IOException ioe) {
						throw new IllegalArgumentException("CAP file " + capFileUrl + " does not seem to exist.", ioe);
					}
				} else if (args[i].equals("-install")) {
					i++;
					int totalOpts = 4;
					int current = 0;
					AID appletAID = null;
					AID packageAID = null;
					int priv = 0;
					byte[] param = null;
					while (i < args.length && current < totalOpts) {
						if (args[i].equals("-applet")) {
							i++;
							byte[] aid = GPUtils.stringToByteArray(args[i]);
							if (aid == null) {
								aid = GPUtils.readableStringToByteArray(args[i]);
							}
							i++;
							if (aid == null) {
								throw new IllegalArgumentException("Malformed AID: " + args[i]);
							}
							appletAID = new AID(aid);
							current = 1;
						} else if (args[i].equals("-package")) {
							i++;
							byte[] aid = GPUtils.stringToByteArray(args[i]);
							if (aid == null) {
								aid = GPUtils.readableStringToByteArray(args[i]);
							}
							i++;
							if (aid == null) {
								throw new IllegalArgumentException("Malformed AID: " + args[i]);
							}
							packageAID = new AID(aid);
							current = 2;
						} else if (args[i].equals("-priv")) {
							i++;
							priv = Integer.parseInt(args[i]);
							i++;
							current = 3;
						} else if (args[i].equals("-param")) {
							i++;
							param = GPUtils.stringToByteArray(args[i]);
							i++;
							if (param == null) {
								throw new IllegalArgumentException("Malformed params: " + args[i]);
							}
							current = 4;
						} else {
							current = 4;
							i--;
						}
					}
					InstallEntry inst = new InstallEntry();
					inst.appletAID = appletAID;
					inst.packageAID = packageAID;
					inst.priv = priv;
					inst.params = param;
					installs.add(inst);
				} else {
					String[] keysOpt = { "-enc", "-mac", "-kek" };
					int index = -1;
					for (int k = 0; k < keysOpt.length; k++) {
						if (args[i].equals(keysOpt[k]))
							index = k;
					}
					if (index >= 0) {
						i++;
						keys[index] = GPUtils.stringToByteArray(args[i]);
						if (keys[index] == null || keys[index].length != 16) {
							throw new IllegalArgumentException("Wrong " + keysOpt[index].substring(1).toUpperCase() + " key: " + args[i]);
						}
					} else {
						throw new IllegalArgumentException("Unknown option: " + args[i]);
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			usage();
			System.exit(1);
		}

		try {
			// Set necessary parameters for seamless PC/SC access.
			// This does not work with multiarch and is not needed with OpenJDK 7
			if (System.getProperty("os.name").equalsIgnoreCase("Linux")) {
				if (new File("/usr/lib/libpcsclite.so").exists()) {
					// Debian
					System.setProperty("sun.security.smartcardio.library", "/usr/lib/libpcsclite.so");
				} else if (new File("/lib/libpcsclite.so").exists()) {
					// Ubuntu
					System.setProperty("sun.security.smartcardio.library", "/lib/libpcsclite.so");
				}
			}
			
			TerminalFactory tf = TerminalFactory.getInstance("PC/SC", null);
			CardTerminals terminals = tf.terminals();
			List<CardTerminal> terms = terminals.list();

			// list readers in verbose mode and with -readers
			if (((terms.size() > 0) && verbose) || listReaders) {
				System.out.println("# Detected readers");
				for (CardTerminal term : terms) {
					System.out.println(term.getName());
				}
				System.out.println();
			}
			
			// Use State.ALL because older OS X Java did now list readers with cards through Java.
			for (CardTerminal terminal : terminals.list(CardTerminals.State.ALL)) {
				Card c = null;
				try {
					// Wrap the terminal with a loggin wrapper if needed.
					if (verbose) {
						terminal = LoggingCardTerminal.getInstance(terminal);
					}

					try {
						c = terminal.connect("*");
					} catch (CardException e) {
						if (e.getCause().getMessage().equalsIgnoreCase("SCARD_E_NO_SMARTCARD")) {
							System.err.println("No card in reader \"" + terminal.getName() + "\": " + e.getCause().getMessage());
							continue;
						} else if (e.getCause().getMessage().equalsIgnoreCase("SCARD_W_UNPOWERED_CARD")) {
							System.err.println("No card in reader \"" + terminal.getName() + "\": " + e.getCause().getMessage());
							System.err.println("  TIP: Make sure that the card is properly inserted and the chip is clean!");
							continue;
						} else {
							System.err.println("Could not read card!");
							e.printStackTrace();
							continue;
						}
					}

					System.out.println("Found card in reader: " + terminal.getName());
					System.out.println("ATR: " + GPUtils.byteArrayToString(c.getATR().getBytes()));
					CardChannel channel = c.getBasicChannel();
					GlobalPlatform service = (sdAID == null) ? new GlobalPlatform(channel) : new GlobalPlatform(sdAID, channel);
					service.open();
					service.setKeys(keySet, keys[0], keys[1], keys[2], diver);
					
					// TODO: make the APDU mode a parameter, properly adjust
					// loadSize accordingly
					int neededExtraSize = apduMode == GlobalPlatform.APDU_CLR ? 0 : (apduMode == GlobalPlatform.APDU_MAC ? 8 : 16);
					if (loadSize + neededExtraSize > GlobalPlatform.defaultLoadSize) {
						loadSize -= neededExtraSize;
					}
					service.openSecureChannel(keySet, 0, GlobalPlatform.SCP_ANY, apduMode);
					// Try to read Card Data and discover actual SD AID
					service.discoverCardProperties();
					
					AIDRegistry registry = service.getStatus();
				
					if (deleteAID.size() > 0) {
						for (AID aid : deleteAID) {
							try {
								service.deleteAID(aid, deleteDeps);
							} catch (GPException gpe) {
								if (!registry.entries.contains(aid)) {
									System.out.println("Could not delete AID (not present on card): " + aid);
								} else {
									System.out.println("Could not delete AID: " + aid);
									gpe.printStackTrace();
								}
							}
						}
					} else if (format) {
						for (AIDRegistryEntry entry : registry.allPackages()) {
							try {
								service.deleteAID(entry.getAID(), true);
							} catch (GPException e) {
								System.out.println("Could not delete AID when formatting: " + entry.getAID() + " : 0x" + Integer.toHexString(e.sw));
							}
						}
					}
					CapFile cap = null;

					if (capFileUrl != null) {
						cap = new CapFile(capFileUrl.openStream());
						service.loadCapFile(cap, loadDebug, loadCompSep, loadSize, loadParam, useHash);
					}

					if (installs.size() > 0) {
						for (InstallEntry install : installs) {
							if (install.appletAID == null) {
								AID p = cap.getPackageAID();
								for (AID a : cap.getAppletAIDs()) {
									service.installAndMakeSelecatable(p, a, null, (byte) install.priv, install.params, null);
								}
							} else {
								service.installAndMakeSelecatable(install.packageAID, install.appletAID, null, (byte) install.priv,
										install.params, null);

							}
						}

					}
					if (listApplets) {
						registry = service.getStatus();
						for (AIDRegistryEntry e : registry) {
							AID aid = e.getAID();
							System.out.println("AID: " + GPUtils.byteArrayToString(aid.getBytes()) + " (" + GPUtils.byteArrayToReadableString(aid.getBytes()) + ")");
							System.out.println("     " + e.getKind().toShortString() + " " + e.getLifeCycleString() + ": " + e.getPrivilegesString());
							
							for (AID a : e.getExecutableAIDs()) {
								System.out.println("     " + GPUtils.byteArrayToString(a.getBytes()) + " (" + GPUtils.byteArrayToReadableString(a.getBytes()) + ")");
							}
							System.out.println();
						}
					}
				} catch (Exception ce) {
					ce.printStackTrace();
				} finally {
					// javax.smartcardio is buggy
					String jvm_version = System.getProperty("java.version");
					if (jvm_version.startsWith("1.7") || jvm_version.startsWith("1.6")) {
						if (c != null) {
							c.disconnect(false);
						}
					}
				}
			}
		} catch (CardException e) {
			if (e.getCause().getMessage().equalsIgnoreCase("SCARD_E_NO_READERS_AVAILABLE"))
				System.out.println("No smart card readers found");
			else
				e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			if (e.getCause().getMessage().equalsIgnoreCase("SCARD_E_NO_SERVICE"))
				System.out.println("No smart card readers found (PC/SC service not running)");
			else {
				e.printStackTrace();
			}
		} catch (Exception e) {
			System.out.println("Terminated by escaping exception: " + e.getClass().getName());
			e.printStackTrace();
		}

	}

	public static void usage() {
		System.out.println("Usage:");
		System.out.println("  java -jar openkms-globalplatform.jar <options>");
		System.out.println("");
		System.out.println("Options:");
		System.out.println(" -v|-verbose       Print APDU-s exchanged with the card");
		System.out.println(" -readers          Print all found card raders");
		System.out.println(" -sdaid <aid>      Security Domain AID, default a000000003000000");
		System.out.println(" -keyset <num>     use key set <num>, default 0");
		System.out.println(" -mode <apduMode>  use APDU mode, CLR, MAC, or ENC, default CLR");
		System.out.println(" -enc <key>        define ENC key, default: 40..4F");
		System.out.println(" -mac <key>        define MAC key, default: 40..4F");
		System.out.println(" -kek <key>        define KEK key, default: 40..4F");
		System.out.println(" -visa2            use VISA2 key diversification (only key set 0), default off");
		System.out.println(" -emv              use EMV key diversification (only key set 0), default off");
		System.out.println(" -deletedeps       also delete depending packages/applets, default off");
		System.out.println(" -delete <aid>     delete package/applet");
		System.out.println(" -load <cap>       load <cap> file to the card, <cap> can be file name or URL");
		System.out.println(" -loadsize <num>   load block size, default " + GlobalPlatform.defaultLoadSize);
		System.out.println(" -loadsep          load CAP components separately, default off");
		System.out.println(" -loaddebug        load the Debug & Descriptor component, default off");
		System.out.println(" -loadparam        set install for load code size parameter");
		System.out.println("                      (e.g. for CyberFlex cards), default off");
		System.out.println(" -loadhash         check code hash during loading");
		System.out.println(" -install          install applet:");
		System.out.println("   -applet <aid>   applet AID, default: take all AIDs from the CAP file");
		System.out.println("   -package <aid>  package AID, default: take from the CAP file");
		System.out.println("   -priv <num>     privileges, default 0");
		System.out.println("   -param <bytes>  install parameters, default: C900");
		System.out.println(" -list             list card registry");
		System.out.println(" -h|-help|--help   print this usage info");
		System.out.println("");
		System.out.println("Multiple -load/-install/-delete and -list take the following precedence:");
		System.out.println("  delete(s), load, install(s), list");
		System.out.println("");
		System.out.println("All -load/-install/-delete/-list actions will be performed on");
		System.out.println("the basic logical channel of all cards currently connected.");
		System.out.println("By default all connected PC/SC readers are searched.");
		System.out.println("");
		System.out.println("<aid> can be of the byte form 0A00000003... or the string form \"|applet.app|\"");
		System.out.println("");
		System.out.println("Examples:");
		System.out.println("");
		System.out.println("  [prog] -list");
		System.out.println("  [prog] -load applet.cap -install -list");
		System.out.println("  [prog] -deletedeps -delete 360000000001 -load applet.cap -install -list");
		System.out.println("  [prog] -emv -keyset 0 -enc 404142434445464748494A4B4C4D4E4F -list");
		System.out.println("");
	}
}
