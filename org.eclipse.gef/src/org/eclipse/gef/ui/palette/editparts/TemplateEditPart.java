package org.eclipse.gef.ui.palette.editparts;

import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.accessibility.AccessibleControlEvent;
import org.eclipse.swt.accessibility.AccessibleEvent;
import org.eclipse.swt.graphics.Image;

import org.eclipse.draw2d.Border;
import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.MarginBorder;

import org.eclipse.gef.AccessibleEditPart;
import org.eclipse.gef.palette.PaletteEntry;
import org.eclipse.gef.palette.PaletteTemplateEntry;

/**
 * A PaletteEditPart that has a {@link PaletteTemplateEntry} for its model and
 * a {@link Label} for its figure.  This is used for dragging template objects 
 * from the palette to a GraphicalViewer to create new objects.
 * 
 * @author Eric Bordeau
 */
public class TemplateEditPart
	extends PaletteEditPart
{

private static Border BORDER = new MarginBorder(3,0,3,0);

public TemplateEditPart(PaletteTemplateEntry entry) {
	super(entry);
}

protected AccessibleEditPart createAccessible() {
	return new AccessibleGraphicalEditPart (){
		public void getDescription(AccessibleEvent e) {
			e.result = getTemplateEntry().getDescription();
		}

		public void getName(AccessibleEvent e) {
			e.result = getTemplateEntry().getLabel();
		}

		public void getRole(AccessibleControlEvent e) {
			e.detail = ACC.ROLE_LISTITEM;
		}
	};
}

public IFigure createFigure() {
	return new DetailedLabelFigure();
			}

private PaletteTemplateEntry getTemplateEntry() {
	return (PaletteTemplateEntry)getModel();
}

protected void refreshVisuals() {
	DetailedLabelFigure fig = (DetailedLabelFigure)getFigure();
	PaletteEntry entry = getPaletteEntry();
	fig.setName(entry.getLabel());
	fig.setDescription(entry.getDescription());
	if (getPreferenceSource().useLargeIcons())
		setImageDescriptor(entry.getLargeIcon());
	else
		setImageDescriptor(entry.getSmallIcon());
	fig.setLayoutMode(getPreferenceSource().getLayoutSetting());
	super.refreshVisuals();
}

public void setFocus(boolean value) {
	super.setFocus(value);
	DetailedLabelFigure label = (DetailedLabelFigure)getFigure();
	if (value)
		label.setSelected(DetailedLabelFigure.SELECTED_WITH_FOCUS);
	else
		label.setSelected(DetailedLabelFigure.SELECTED_WITHOUT_FOCUS);
}

/**
 * @see org.eclipse.gef.ui.palette.editparts.PaletteEditPart#setImageInFigure(Image)
 */
protected void setImageInFigure(Image image) {
	DetailedLabelFigure fig = (DetailedLabelFigure)getFigure();
	fig.setImage(image);
}

public void setSelected(int value) {
	super.setSelected(value);
	DetailedLabelFigure label = (DetailedLabelFigure)getFigure();
	if( value == SELECTED_PRIMARY )
		label.setSelected(DetailedLabelFigure.SELECTED_WITH_FOCUS);
	else if( value == SELECTED )
		label.setSelected(DetailedLabelFigure.SELECTED_WITHOUT_FOCUS);
	else {
		label.setSelected(DetailedLabelFigure.NOT_SELECTED);
	}		
}

}
