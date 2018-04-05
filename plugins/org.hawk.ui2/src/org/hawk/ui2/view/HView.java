/*******************************************************************************
 * Copyright (c) 2011-2015 The University of York.
 * 
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 3.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-3.0
 *
 * Contributors:
 *     Seyyed Shah - initial API and implementation
 *     Konstantinos Barmpis - updates and maintenance
 ******************************************************************************/
package org.hawk.ui2.view;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Iterator;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IContributionManager;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.jface.wizard.IWizard;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.wizards.IWizardDescriptor;
import org.hawk.core.IModelIndexer.ShutdownRequestType;
import org.hawk.core.IStateListener.HawkState;
import org.hawk.osgiserver.HModel;
import org.hawk.ui2.Activator;
import org.hawk.ui2.dialog.HConfigDialog;
import org.hawk.ui2.dialog.HImportDialog;
import org.hawk.ui2.dialog.HQueryDialog;
import org.hawk.ui2.util.HUIManager;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.prefs.BackingStoreException;

/**
 * This sample class demonstrates how to plug-in a new workbench view. The view
 * shows data obtained from the model. The sample creates a dummy model on the
 * fly, but a real implementation would connect to the model available either in
 * this or another plug-in (e.g. the workspace). The view is connected to the
 * model using a content provider.
 * <p>
 * The view uses a label provider to define how model objects should be
 * presented in the view. Each view can present the same model objects using
 * different labels and icons, if needed. Alternatively, a single label provider
 * can be shared between views in order to ensure that objects of the same type
 * are presented in the same way everywhere.
 * <p>
 */

public class HView extends ViewPart {

	/**
	 * The ID of the view as specified by the extension.
	 */
	public static final String ID = "hawk.ui.view.HawkView";

	private TableViewer viewer;
	private Action query;
	private Action start;
	private Action stop;
	private Action sync;
	private Action delete;
	private Action add;
	private Action config;
	private Action importRepos;

	// private Action doubleClickAction;

	/*
	 * The content provider class is responsible for providing objects to the
	 * view. It can wrap existing objects in adapters or simply return objects
	 * as-is. These objects may be sensitive to the current input of the view,
	 * or ignore it and always show the same content (like Task List, for
	 * example).
	 */

	class ViewLabelProvider extends LabelProvider implements
			ITableLabelProvider {

		protected Image runningImage = null;
		protected Image stoppedImage = null;

		public String getColumnText(Object obj, int index) {
			HModel hModel = (HModel) obj;
			if (index == 0)
				return hModel.getName();
			else if (index == 1)
				return hModel.getFolder();
			else if (index == 2)
				return hModel.getStatus().toString();
			else
				return hModel.getInfo();
		}

		public Image getColumnImage(Object obj, int index) {
			if (index == 0)
				return getImage(obj);
			else
				return null;
		}

		public Image getImage(Object obj) {

			if (runningImage == null) {
				runningImage = Activator.getImageDescriptor(
						"icons/hawk-running.png").createImage();
				stoppedImage = Activator.getImageDescriptor(
						"icons/hawk-stopped.png").createImage();
			}

			HModel hModel = (HModel) obj;

			if (hModel.isRunning()) {
				return runningImage;
			} else {
				return stoppedImage;
			}
		}
	}

	class NameSorter extends ViewerSorter {
	}

	/**
	 * The constructor.
	 */
	public HView() {
	}

	HUIManager hm;

	private Shell shell;

	ViewLabelProvider vlp;

	/**
	 * This is a callback that will allow us to create the viewer and initialize
	 * it.
	 */
	public void createPartControl(Composite parent) {

		shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();

		viewer = new TableViewer(parent, SWT.MULTI | SWT.H_SCROLL
				| SWT.V_SCROLL);
		hm = HUIManager.getInstance();
		// hm.setView(this);
		viewer.setContentProvider(hm);
		vlp = new ViewLabelProvider();
		viewer.setLabelProvider(vlp);
		viewer.setSorter(new NameSorter());
		viewer.setInput(getViewSite());

		TableColumn c = new TableColumn(viewer.getTable(), SWT.FULL_SELECTION);
		c.setText("Name");
		c.setWidth(150);

		c = new TableColumn(viewer.getTable(), SWT.FULL_SELECTION);
		c.setText("Location");
		c.setWidth(450);

		c = new TableColumn(viewer.getTable(), SWT.FULL_SELECTION);
		c.setText("Status");
		c.setWidth(150);

		c = new TableColumn(viewer.getTable(), SWT.FULL_SELECTION);
		c.setText("Info");
		c.setWidth(450);

		viewer.getTable().setHeaderVisible(true);
		viewer.getTable().setLinesVisible(true);

		// Create the help context id for the viewer'hawkState control
		PlatformUI.getWorkbench().getHelpSystem()
				.setHelp(viewer.getControl(), "hawkview.viewer");
		makeActions();
		hookContextMenu();
		hookDoubleClickAction();
		hookPermanentDeleteAction();
		contributeToActionBars();
		viewer.addPostSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(final SelectionChangedEvent event) {
				IStructuredSelection selection = (IStructuredSelection) event
						.getSelection();
				if (selection.isEmpty() || selection.size() > 1)
					initButtons();

				if (selection.size() == 1) {
					enableButtons();

					HModel hmodel = ((HModel) selection.getFirstElement());
					HawkState status = hmodel.getStatus();
					if (status == HawkState.UPDATING) {
						start.setEnabled(false);
						stop.setEnabled(false);
						delete.setEnabled(false);
						sync.setEnabled(false);
						query.setEnabled(false);
						config.setEnabled(false);
					} else {
						final boolean running = hmodel.isRunning();
						start.setEnabled(!running);
						stop.setEnabled(running);
						sync.setEnabled(running);
						query.setEnabled(running);
						config.setEnabled(running);
					}

				}

			}

		});
		viewer.refresh();
	}

	private void hookPermanentDeleteAction() {
		viewer.getTable().addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (viewer.getSelection().isEmpty())
					return;

				if (e.character == SWT.DEL && (e.stateMask & SWT.SHIFT) > 0) {
					if (MessageDialog
							.openConfirm(shell, "Are you sure?",
									"Do you want to permanently delete these instances (including storage)?")) {
						final IStructuredSelection selected = (IStructuredSelection) viewer
								.getSelection();
						for (Iterator<?> it = selected.iterator(); it.hasNext();) {
							final HModel hawkModel = (HModel) it.next();
							try {
								hm.delete(hawkModel, hawkModel.exists());
								removeRecursive(Paths.get(hawkModel.getFolder()));
							} catch (BackingStoreException | IOException ex) {
								ex.printStackTrace();
							}
						}
						viewer.refresh();
					}
				}
			}
		});
	}

	private void initButtons() {
		query.setEnabled(false);
		start.setEnabled(false);
		stop.setEnabled(false);
		sync.setEnabled(false);
		delete.setEnabled(false);

		config.setEnabled(false);
	}

	private void enableButtons() {
		query.setEnabled(true);
		start.setEnabled(true);
		stop.setEnabled(true);
		sync.setEnabled(true);
		delete.setEnabled(true);

		config.setEnabled(true);
	}

	private void hookContextMenu() {
		MenuManager menuMgr = new MenuManager("#PopupMenu");
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			public void menuAboutToShow(IMenuManager manager) {
				HView.this.populateContributionManager(manager);
			}
		});
		Menu menu = menuMgr.createContextMenu(viewer.getControl());
		viewer.getControl().setMenu(menu);
		getSite().registerContextMenu(menuMgr, viewer);
	}

	private void contributeToActionBars() {
		IActionBars bars = getViewSite().getActionBars();
		populateContributionManager(bars.getMenuManager());
		populateContributionManager(bars.getToolBarManager());
	}

	private void populateContributionManager(IContributionManager manager) {
		manager.add(query);
		manager.add(start);
		manager.add(stop);
		manager.add(sync);
		manager.add(delete);
		manager.add(add);
		manager.add(importRepos);
		manager.add(config);
	}

	private void makeActions() {
		query = new Action() {
			public void run() {
				IStructuredSelection selected = (IStructuredSelection) viewer
						.getSelection();

				HQueryDialog dialog = new HQueryDialog(shell,
						(HModel) selected.getFirstElement());
				dialog.open();
			}

		};
		query.setText("Query");
		query.setToolTipText("Query");
		query.setImageDescriptor(ImageDescriptor.createFromURL(FileLocator
				.find(FrameworkUtil.getBundle(this.getClass()), new Path(
						"icons/query.gif"), null)));

		start = new Action() {
			public void run() {
				IStructuredSelection selected = (IStructuredSelection) viewer
						.getSelection();
				if (selected.size() == 1) {
					final HModel hawkModel = (HModel) selected
							.getFirstElement();
					if (hawkModel.start(hm)) {
						viewer.refresh();
						start.setEnabled(false);
						stop.setEnabled(true);
						sync.setEnabled(true);
						query.setEnabled(true);
						config.setEnabled(true);
					} else {
						viewer.refresh();
					}
				}
			}
		};
		start.setText("Start");
		start.setToolTipText("Start");
		start.setImageDescriptor(ImageDescriptor.createFromURL(FileLocator
				.find(FrameworkUtil.getBundle(this.getClass()), new Path(
						"icons/nav_go.gif"), null)));

		stop = new Action() {
			public void run() {
				IStructuredSelection selected = (IStructuredSelection) viewer
						.getSelection();
				if (selected.size() == 1) {
					((HModel) selected.getFirstElement())
							.stop(ShutdownRequestType.ALWAYS);
					viewer.refresh();
					start.setEnabled(true);
					stop.setEnabled(false);
					sync.setEnabled(false);
					query.setEnabled(false);
					config.setEnabled(false);
				}
			}
		};
		stop.setText("Stop");
		stop.setToolTipText("Stop");
		stop.setImageDescriptor(ImageDescriptor.createFromURL(FileLocator.find(
				FrameworkUtil.getBundle(this.getClass()), new Path(
						"icons/stop.gif"), null)));

		sync = new Action() {
			public void run() {
				IStructuredSelection selected = (IStructuredSelection) viewer
						.getSelection();
				if (selected.size() == 1) {
					try {
						((HModel) selected.getFirstElement()).sync();
					} catch (Exception e) {
						Activator.logError("Failed to invoke manual sync", e);
					}
				}
			}
		};
		sync.setText("Sync");
		sync.setToolTipText("Sync");
		sync.setImageDescriptor(ImageDescriptor.createFromURL(FileLocator.find(
				FrameworkUtil.getBundle(this.getClass()), new Path(
						"icons/refresh.gif"), null)));

		delete = new Action() {
			public void run() {
				IStructuredSelection selected = (IStructuredSelection) viewer
						.getSelection();
				if (selected.size() == 1) {
					HModel hawkmodel = (HModel) selected.getFirstElement();
					try {
						hm.delete(hawkmodel, hawkmodel.exists());
					} catch (BackingStoreException e) {
						e.printStackTrace();
					}
					viewer.refresh();
				}
			}
		};
		delete.setText("Delete");
		delete.setToolTipText("Delete");
		delete.setImageDescriptor(ImageDescriptor.createFromURL(FileLocator
				.find(FrameworkUtil.getBundle(this.getClass()), new Path(
						"icons/rem_co.gif"), null)));

		add = new Action() {
			public void run() {
				IWizardDescriptor descriptor = PlatformUI.getWorkbench()
						.getNewWizardRegistry()
						.findWizard("hawk.ui.wizard.HawkNewWizard");
				if (descriptor != null) {
					IWizard wizard;
					try {
						wizard = descriptor.createWizard();
						WizardDialog wd = new WizardDialog(shell, wizard);
						wd.setTitle(wizard.getWindowTitle());
						wd.open();
					} catch (CoreException e) {
						e.printStackTrace();
					}
				}
			}
		};
		add.setText("Add");
		add.setToolTipText("Add");
		add.setImageDescriptor(ImageDescriptor.createFromURL(FileLocator.find(
				FrameworkUtil.getBundle(this.getClass()), new Path(
						"icons/new-hawk.png"), null)));

		config = new Action() {
			public void run() {
				IStructuredSelection selected = (IStructuredSelection) viewer
						.getSelection();

				HConfigDialog dialog = new HConfigDialog(PlatformUI
						.getWorkbench().getActiveWorkbenchWindow().getShell(),
						((HModel) selected.getFirstElement()));
				dialog.open();
			}
		};
		config.setText("Configure");
		config.setToolTipText("Configure");
		config.setImageDescriptor(ImageDescriptor.createFromURL(FileLocator
				.find(FrameworkUtil.getBundle(this.getClass()), new Path(
						"icons/configure.gif"), null)));

		initButtons();

		importRepos = new Action() {
			public void run() {
				final HImportDialog dialog = new HImportDialog(shell);
				dialog.open();
			}

		};
		importRepos.setText("Import");
		importRepos.setToolTipText("Import existing instances");
		importRepos.setImageDescriptor(ImageDescriptor
				.createFromURL(FileLocator.find(FrameworkUtil.getBundle(this
						.getClass()), new Path("icons/import_wiz.png"), null)));
	}

	private void hookDoubleClickAction() {
		viewer.addDoubleClickListener(new IDoubleClickListener() {
			public void doubleClick(DoubleClickEvent event) {
				if (config.isEnabled())
					config.run();
				else if (start.isEnabled())
					start.run();
			}
		});
	}

	// private void showMessage(String message) {
	// MessageDialog.openInformation(viewer.getControl().getShell(), "Hawk",
	// message);
	// }

	/**
	 * Passing the focus request to the viewer'hawkState control.
	 */
	public void setFocus() {
		viewer.getControl().setFocus();
	}

	public void update() {

		// disable buttons if updating
		IStructuredSelection selected = (IStructuredSelection) viewer
				.getSelection();
		if (selected.size() == 1) {
			HModel hmodel = ((HModel) selected.getFirstElement());
			HawkState status = hmodel.getStatus();
			if (status == HawkState.UPDATING) {
				start.setEnabled(false);
				stop.setEnabled(false);
				delete.setEnabled(false);
				sync.setEnabled(false);
				query.setEnabled(false);
				config.setEnabled(false);
			} else {
				final boolean running = hmodel.isRunning();
				start.setEnabled(!running);
				stop.setEnabled(running);
				sync.setEnabled(running);
				query.setEnabled(running);
				config.setEnabled(running);
			}
		}

		viewer.refresh();
	}

	public static void updateAsync(Display display) {
		display.asyncExec(new Runnable() {
			public void run() {
				try {
					IWorkbenchPage page = PlatformUI.getWorkbench()
							.getActiveWorkbenchWindow().getActivePage();
					try {
						HView view = (HView) page.findView(HView.ID);
						if (view != null) {
							// View might not be open (e.g. Hawk is started through other means)
							view.update();
						}
					} catch (Exception e) {
						Activator.logError(e.getMessage(), e);
					}
				} catch (Throwable t) {
					// suppress
				}
			}
		});
	}

	private static void removeRecursive(java.nio.file.Path path)
			throws IOException {
		Files.walkFileTree(path, new SimpleFileVisitor<java.nio.file.Path>() {
			@Override
			public FileVisitResult visitFile(java.nio.file.Path file,
					BasicFileAttributes attrs) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(java.nio.file.Path file,
					IOException exc) throws IOException {
				// try to delete the file anyway, even if its attributes
				// could not be read, since delete-only access is
				// theoretically possible
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(java.nio.file.Path dir,
					IOException exc) throws IOException {
				if (exc == null) {
					Files.delete(dir);
					return FileVisitResult.CONTINUE;
				} else {
					// directory iteration failed; propagate exception
					throw exc;
				}
			}
		});
	}
}