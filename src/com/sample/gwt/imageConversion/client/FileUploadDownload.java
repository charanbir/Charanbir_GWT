package com.sample.gwt.imageConversion.client;

import java.util.logging.Logger;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FileUpload;
import com.google.gwt.user.client.ui.FormPanel;
import com.google.gwt.user.client.ui.FormPanel.SubmitCompleteEvent;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.VerticalPanel;

/**
 * Entry point classes define <code>onModuleLoad()</code>.
 */
public class FileUploadDownload implements EntryPoint {
	Logger log = Logger.getLogger(FileUploadDownload.class.getName());

	private static final String ACTION = GWT.getModuleBaseURL()
			+ "uploadDownload";

	@Override
	public void onModuleLoad() {
		final FormPanel form = new FormPanel();
		form.setAction(ACTION);

		form.setEncoding(FormPanel.ENCODING_MULTIPART);
		form.setMethod(FormPanel.METHOD_POST);

		VerticalPanel panel = new VerticalPanel();
		form.setWidget(panel);

		final FileUpload upload = new FileUpload();
		upload.setName("uploadDownloadFormElement");
		panel.add(upload);
		panel.add(new Button("Upload PNG", new ClickHandler() {
			public void onClick(ClickEvent event) {
				form.submit();
			}
		}));
		panel.add(new Anchor("Download Converted Files", false, ACTION, "_blank"));

		form.addSubmitCompleteHandler(new FormPanel.SubmitCompleteHandler() {
			public void onSubmitComplete(SubmitCompleteEvent event) {
				Window.alert(event.getResults());
			}
		});

		RootPanel.get().add(form);

	}
}
