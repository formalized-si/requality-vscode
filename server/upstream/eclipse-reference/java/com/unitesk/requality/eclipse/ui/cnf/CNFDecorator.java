package com.unitesk.requality.eclipse.ui.cnf;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.viewers.DecoratingLabelProvider;
import org.eclipse.jface.viewers.ILabelDecorator;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.swt.graphics.Image;

import com.unitesk.requality.core.Consts;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.core.storage.ProjectLoadingState;
import com.unitesk.requality.eclipse.core.EclipseTracker;
import com.unitesk.requality.eclipse.natures.ReqCatalogue;
import com.unitesk.requality.tool.CoreUtils;

public class CNFDecorator extends DecoratingLabelProvider implements ILabelProvider {
	 
	ILabelProvider provider;
	ILabelDecorator decorator;
	/**
	* @param provider
	* @param decorator
	*/
	public CNFDecorator(ILabelProvider provider,
	    ILabelDecorator decorator) {
	  super(provider, decorator);
	  this.provider = (ILabelProvider) provider;
	  this.decorator = decorator;
	}
	 
	/* (non-Javadoc)
	* @see org.eclipse.jface.viewers.ITableLabelProvider#getColumnImage(java.lang.Object, int)
	*/
	public Image getImage(Object element, int columnIndex) {
//      if(element instanceof NotLoadedReqProject)
//        return PlatformUI.getWorkbench().getSharedImages().getImage(IDE.SharedImages.IMG_OBJ_PROJECT_CLOSED);
      return super.getImage(element);
	}
	@Override
	public String getText(Object element) {
      String text = null;
      if(element instanceof IProject) {
        IProject el = ((IProject)element);
        EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
        try {
          if(el.exists() && el.isOpen() && el.hasNature(ReqCatalogue.ID)) {
            if(el.hasNature(Consts.CATALOG_BROKEN_NATURE))
              text = provider.getText(el)+" ("+Consts.BROKEN_POSTFIX+")";
            else
              if(et.getProjectLoadingState(el.getLocationURI())==ProjectLoadingState.LOADED) {
                if(el.getLocation()!=null) {
                  String cu = CoreUtils.getProjectName(el.getLocation().toFile());
                  if(cu!=null) 
                    text = cu;
                  }
              }else
                text = provider.getText(el)+" ("+Consts.LOADING_POSTFIX+")";
          }
		} catch (CoreException e) {
          RLogger.logError(CoreUtils.getTrace(e));
		}
      }
      if(text == null)
        text = provider.getText(element);
	  if (decorator != null) {
	    String decorated = decorator.decorateText(text, element);
	    if (decorated != null) {
	      return decorated;
	    }
	  }
	  return text;
	}
}
