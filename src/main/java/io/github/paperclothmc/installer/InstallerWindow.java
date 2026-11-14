/*
 * Copyright (c) 2026 PaperClothMC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.paperclothmc.installer;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;

final class InstallerWindow {
	private final String metaLocation;
	private final JFrame frame = new JFrame("PaperCloth Installer");
	private final JComboBox<Meta.MinecraftVersion> minecraft = new JComboBox<Meta.MinecraftVersion>();
	private final JCheckBox snapshots = new JCheckBox("Show snapshots");
	private final JComboBox<Meta.Release> release = new JComboBox<Meta.Release>();
	private static final String LAST_LOCATION = "lastInstallLocation";
	private final JTextField location = new JTextField(lastLocation(), 26);
	private final JButton browse = new JButton("...");
	private final JLabel status = new JLabel("Loading versions...", SwingConstants.CENTER);
	private final JProgressBar progress = new JProgressBar();
	private final JButton install = new JButton("Install");
	private Meta meta;

	private InstallerWindow(String metaLocation) {
		this.metaLocation = metaLocation;
	}

	static void open(final String metaLocation) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				try {
					UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
				} catch (Exception ignored) {
					UIManager.getLookAndFeelDefaults();
				}

				new InstallerWindow(metaLocation).show();
			}
		});
	}

	private void show() {
		JPanel server = new JPanel(new GridBagLayout());
		server.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(5, 5, 5, 5);
		c.anchor = GridBagConstraints.LINE_END;

		c.gridx = 0;
		c.gridy = 0;
		server.add(new JLabel("Minecraft Version:"), c);
		c.gridx = 1;
		c.anchor = GridBagConstraints.LINE_START;
		c.fill = GridBagConstraints.HORIZONTAL;
		server.add(this.minecraft, c);
		c.gridx = 2;
		c.fill = GridBagConstraints.NONE;
		server.add(this.snapshots, c);

		c.gridx = 0;
		c.gridy = 1;
		c.anchor = GridBagConstraints.LINE_END;
		server.add(new JLabel("PaperCloth Version:"), c);
		c.gridx = 1;
		c.gridwidth = 2;
		c.anchor = GridBagConstraints.LINE_START;
		c.fill = GridBagConstraints.HORIZONTAL;
		server.add(this.release, c);
		c.gridwidth = 1;

		c.gridx = 0;
		c.gridy = 2;
		c.anchor = GridBagConstraints.LINE_END;
		c.fill = GridBagConstraints.NONE;
		server.add(new JLabel("Select Install Location:"), c);
		JPanel path = new JPanel(new BorderLayout(6, 0));
		path.add(this.location, BorderLayout.CENTER);
		path.add(this.browse, BorderLayout.LINE_END);
		c.gridx = 1;
		c.gridwidth = 2;
		c.anchor = GridBagConstraints.LINE_START;
		c.fill = GridBagConstraints.HORIZONTAL;
		server.add(path, c);

		c.gridx = 0;
		c.gridy = 3;
		c.gridwidth = 3;
		c.anchor = GridBagConstraints.CENTER;
		server.add(this.status, c);

		c.gridy = 4;
		this.progress.setPreferredSize(new Dimension(320, 12));
		this.progress.setVisible(false);
		server.add(this.progress, c);

		c.gridy = 5;
		c.fill = GridBagConstraints.NONE;
		this.install.setPreferredSize(new Dimension(110, this.install.getPreferredSize().height));
		server.add(this.install, c);

		JTabbedPane tabs = new JTabbedPane();
		tabs.addTab("Server", server);
		this.frame.getContentPane().add(tabs);

		this.snapshots.addActionListener(event -> this.fillMinecraft());
		this.minecraft.addActionListener(event -> this.fillReleases());
		this.browse.addActionListener(event -> this.chooseFolder());
		this.install.addActionListener(event -> this.install());
		this.setBusy(true);

		this.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.frame.pack();
		this.frame.setMinimumSize(this.frame.getSize());
		this.frame.setLocationRelativeTo(null);
		this.frame.setVisible(true);
		this.loadVersions();
	}

	private void loadVersions() {
		new SwingWorker<Meta, Void>() {
			@Override
			protected Meta doInBackground() throws Exception {
				return Meta.load(InstallerWindow.this.metaLocation);
			}

			@Override
			protected void done() {
				try {
					InstallerWindow.this.meta = this.get();
					InstallerWindow.this.fillMinecraft();
					InstallerWindow.this.setBusy(false);
					InstallerWindow.this.status.setText(InstallerWindow.this.meta.message != null ? InstallerWindow.this.meta.message : "Ready to install");
				} catch (InterruptedException | ExecutionException e) {
					InstallerWindow.this.status.setText("Could not load the version list");
					InstallerWindow.this.error("Could not load the version list from\n" + InstallerWindow.this.metaLocation + "\n\n" + cause(e));
				}
			}
		}.execute();
	}

	private void fillMinecraft() {
		if (this.meta == null) {
			return;
		}

		Object selected = this.minecraft.getSelectedItem();
		List<Meta.MinecraftVersion> versions = this.meta.minecraftVersions(this.snapshots.isSelected());
		this.minecraft.setModel(new DefaultComboBoxModel<Meta.MinecraftVersion>(versions.toArray(new Meta.MinecraftVersion[0])));

		for (Meta.MinecraftVersion version : versions) {
			if (selected instanceof Meta.MinecraftVersion && version.id.equals(((Meta.MinecraftVersion) selected).id)) {
				this.minecraft.setSelectedItem(version);
			}
		}

		this.fillReleases();
	}

	private void fillReleases() {
		Meta.MinecraftVersion selected = (Meta.MinecraftVersion) this.minecraft.getSelectedItem();
		List<Meta.Release> releases = this.meta == null || selected == null ? java.util.Collections.<Meta.Release>emptyList() : this.meta.releasesFor(selected.id, true);
		this.release.setModel(new DefaultComboBoxModel<Meta.Release>(releases.toArray(new Meta.Release[0])));

		for (Meta.Release candidate : releases) {
			if (candidate.stable) {
				this.release.setSelectedItem(candidate);
				break;
			}
		}

		this.install.setEnabled(this.meta != null && !releases.isEmpty());
	}

	private void chooseFolder() {
		JFileChooser chooser = new JFileChooser(this.location.getText());
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setDialogTitle("Select Install Location");

		if (chooser.showOpenDialog(this.frame) == JFileChooser.APPROVE_OPTION) {
			this.location.setText(chooser.getSelectedFile().getPath());
		}
	}

	private void install() {
		final Meta.Release chosen = (Meta.Release) this.release.getSelectedItem();
		final File folder = new File(this.location.getText().trim());

		if (chosen == null || this.location.getText().trim().isEmpty()) {
			return;
		}

		this.setBusy(true);
		this.progress.setVisible(true);
		this.progress.setValue(0);
		this.frame.pack();

		new SwingWorker<Void, Object[]>() {
			@Override
			protected Void doInBackground() throws Exception {
				ServerInstall.install(chosen, folder, new ServerInstall.Listener() {
					@Override
					public void status(String message) {
						publish(new Object[] {message});
					}

					@Override
					public void progress(int done, int total) {
						publish(new Object[] {done, total});
					}
				});
				return null;
			}

			@Override
			protected void process(List<Object[]> updates) {
				for (Object[] update : updates) {
					if (update.length == 1) {
						InstallerWindow.this.status.setText((String) update[0]);
					} else {
						InstallerWindow.this.progress.setMaximum((Integer) update[1]);
						InstallerWindow.this.progress.setValue((Integer) update[0]);
					}
				}
			}

			@Override
			protected void done() {
				InstallerWindow.this.setBusy(false);
				InstallerWindow.this.progress.setVisible(false);
				InstallerWindow.this.frame.pack();

				try {
					this.get();
					String java = chosen.java > 0 ? " It needs Java " + chosen.java + " or newer." : "";
					InstallerWindow.this.status.setText("Installed PaperCloth " + chosen.version);
					JOptionPane.showMessageDialog(InstallerWindow.this.frame,
							"PaperCloth " + chosen.version + " for Minecraft " + chosen.minecraft + " is installed in\n" + folder.getAbsolutePath()
									+ "\n\nStart it with start.bat or start.sh." + java
									+ "\nThe first start creates eula.txt; set eula=true after reading Mojang's EULA.",
							"PaperCloth Installer", JOptionPane.INFORMATION_MESSAGE);
				} catch (InterruptedException | ExecutionException e) {
					InstallerWindow.this.status.setText("Install failed");
					InstallerWindow.this.error("The install failed:\n\n" + cause(e));
				}
			}
		}.execute();
	}

	private void setBusy(boolean busy) {
		this.minecraft.setEnabled(!busy);
		this.snapshots.setEnabled(!busy);
		this.release.setEnabled(!busy);
		this.location.setEnabled(!busy);
		this.browse.setEnabled(!busy);
		this.install.setEnabled(!busy && this.release.getSelectedItem() != null);
	}

	private void error(String message) {
		JOptionPane.showMessageDialog(this.frame, message, "PaperCloth Installer", JOptionPane.ERROR_MESSAGE);
	}

	private static String lastLocation() {
		String fallback = new File(System.getProperty("user.home"), "papercloth-server").getPath();

		try {
			return preferences().get(LAST_LOCATION, fallback);
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	private static void rememberLocation(File folder) {
		try {
			Preferences preferences = preferences();
			preferences.put(LAST_LOCATION, folder.getAbsolutePath());
			preferences.flush();
		} catch (Exception ignored) {
		}
	}

	private static Preferences preferences() {
		return Preferences.userRoot().node("io/github/paperclothmc/installer");
	}

	private static String cause(Exception e) {
		Throwable cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
		return cause.getMessage() != null ? cause.getMessage() : cause.toString();
	}
}
