package net.sf.openrocket.gui.dialogs;

import java.awt.Dialog;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;

import net.miginfocom.swing.MigLayout;
import net.sf.openrocket.gui.adaptors.DoubleModel;
import net.sf.openrocket.gui.components.StyledLabel;
import net.sf.openrocket.gui.components.UnitSelector;
import net.sf.openrocket.material.Material;
import net.sf.openrocket.util.GUIUtil;

public class CustomMaterialDialog extends JDialog {

	private final Material originalMaterial;
	
	private boolean okClicked = false;
	private JComboBox typeBox;
	private JTextField nameField;
	private DoubleModel density;
	private JSpinner densitySpinner;
	private UnitSelector densityUnit;
	private JCheckBox addBox;
	
	public CustomMaterialDialog(Window parent, Material material, boolean saveOption,
			String title) {
		this(parent, material, saveOption, title, null);
	}
	
		
	public CustomMaterialDialog(Window parent, Material material, boolean saveOption,
			String title, String note) {
		super(parent, "Custom material", Dialog.ModalityType.APPLICATION_MODAL);
		
		this.originalMaterial = material;
		
		JPanel panel = new JPanel(new MigLayout("fill, gap rel unrel"));
		
		
		// Add title and note
		if (title != null) {
			panel.add(new JLabel("<html><b>" + title + ":"), 
					"gapleft para, span, wrap" + (note == null ? " para":""));
		}
		if (note != null) {
			panel.add(new StyledLabel(note, -1), "span, wrap para");
		}
		

		// Material name
		panel.add(new JLabel("Material name:"));
		nameField = new JTextField(15);
		if (material != null) {
			nameField.setText(material.getName());
		}
		panel.add(nameField, "span, growx, wrap");
		
		
		// Material type (if not known)
		panel.add(new JLabel("Material type:"));
		if (material == null) {
			typeBox = new JComboBox(Material.Type.values());
			typeBox.setSelectedItem(Material.Type.BULK);
			typeBox.setEditable(false);
			typeBox.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					updateDensityModel();
				}
			});
			panel.add(typeBox, "span, growx, wrap");
		} else {
			panel.add(new JLabel(material.getType().toString()), "span, growx, wrap");
		}
		
		
		// Material density
		panel.add(new JLabel("Material density:"));
		densitySpinner = new JSpinner();
		panel.add(densitySpinner, "w 70lp");
		densityUnit = new UnitSelector((DoubleModel)null);
		panel.add(densityUnit, "w 30lp");
		panel.add(new JPanel(), "growx, wrap");
		updateDensityModel();
		
		
		// Save option
		if (saveOption) {
			addBox = new JCheckBox("Add material to database");
			panel.add(addBox,"span, wrap");
		}
			
		JButton okButton = new JButton("OK");
		okButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				okClicked = true;
				CustomMaterialDialog.this.setVisible(false);
			}
		});
		panel.add(okButton,"span, split, tag ok");
		
		JButton closeButton = new JButton("Cancel");
		closeButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				okClicked = false;
				CustomMaterialDialog.this.setVisible(false);
			}
		});
		panel.add(closeButton,"tag cancel");
		
		this.setContentPane(panel);
		this.pack();
		this.setLocationByPlatform(true);
		GUIUtil.setDisposableDialogOptions(this, okButton);
	}
	
	
	public boolean getOkClicked() {
		return okClicked;
	}
	
	
	public boolean isAddSelected() {
		return addBox.isSelected();
	}
	
	
	public Material getMaterial() {
		Material.Type type;
		String name;
		double density;
		
		if (typeBox != null) {
			type = (Material.Type) typeBox.getSelectedItem();
		} else {
			type = originalMaterial.getType();
		}
		
		name = nameField.getText().trim();
		
		density = this.density.getValue();
		
		return Material.newMaterial(type, name, density, true);
	}
	
	
	private void updateDensityModel() {
		if (originalMaterial != null) {
			if (density == null) {
				density = new DoubleModel(originalMaterial.getDensity(),
						originalMaterial.getType().getUnitGroup(), 0);
				densitySpinner.setModel(density.getSpinnerModel());
				densityUnit.setModel(density);
			}
		} else {
			Material.Type type = (Material.Type) typeBox.getSelectedItem();
			density = new DoubleModel(0, type.getUnitGroup(), 0);
			densitySpinner.setModel(density.getSpinnerModel());
			densityUnit.setModel(density);
		}
	}
}