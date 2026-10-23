// TestDB: CKEditor plugin
// Implements functions used by the UniEditor component of the Requality project
// Details:
//   1. New toolbar button RequalitySave that saves changes and closes the editor.

(function () {
	// Command implementing Linking Test Purpose action
	var linkTP = {
		exec: function (editor) {
			//TODO find existing links
			/*var range = editor.getSelection().getRanges()[0];
			var pos = range.endOffset;
			var text = editor.getSelection().getStartElement().getText();
			var elemtext = range.startContainer.getText();
			pos += text.indexOf(elemtext);
			alert(text+" "+linktoexp);
			if(linktoexp != null){
				while (match = linktoexp.exec(text) != null) {
					alert(pos+" "+match.index);
					if(pos >= match.index){
						alert(123);
						
					}
				}
			}*/
			var elem = $(editor.container.$).closest('.supported-node')[0];
			// Call java action that is calling a dialog and adds link to
			// test purpose in element which was edited
			editorJavaDialog = true;
			try{
				var toadd = addNewTestPurposeToEditor(extractUUID(elem.id));
				/*if (toadd != null){	
					//if test purpose was selected
					editor.insertHtml( '[<span id="' + toadd
			                + '">&zwnj;</span>linkto: ' + getUFName(toadd)+"]" , "unfiltered_html"); 
				}*/
			}
			catch(e){}
			editorJavaDialog = false;
		},
		modes : { wysiwyg : 1, source : 1 }  
	};
	// Register the new plugin named "testDB".
	CKEDITOR.plugins.add('testdb', {
		icons: 'linktestpurpose',
		init: function (editor) {
			editor.addCommand('linkTestPurpose', linkTP);
			editor.ui.addButton('LinkTestPurpose', {
				label: 'Link test purpose to this test step',
				command: 'linkTestPurpose'
			});
		}
	});
})();
