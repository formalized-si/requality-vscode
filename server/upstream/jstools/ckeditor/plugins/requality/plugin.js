// Requality: CKEditor plugin
// Implements functions used by the UniEditor component of the Requality project
// Details:
//   1. New toolbar button RequalitySave that saves changes and closes the editor.
//   2. New toolbar button RequalityExit that closes the editor without saving
//      changes (confirmation message is shown if changes are present).

// TODO: Calling requalitySave/requalityExit causes JS errors. Probably, editor
// instance is destroyed too early. Find out how to work around the problem!


function escapeHtml(unsafe) {
    return unsafe
         .replace(/&/g, "&amp;")
         .replace(/</g, "&lt;")
         .replace(/>/g, "&gt;")
         .replace(/"/g, "&quot;")
         .replace(/'/g, "&#039;");
 }


(function() {
		// Command implementing RequalitySave action
	var saveCmd = {
		exec: function (ed) {
			try{
				var name;
				var editor = ed;
				editor.onClose(ed);
			}catch(ex){printToConsole(ex+" "+ex.trace);}
			return true;
		}//,
		//modes : { wysiwyg : 1, source : 1 }  
	};
	// Command implementing RequalityExit action
	var exitCmd = {
		exec: function (ed) {
			try{
				var editor = ed;
				editor.setData(editor.odata);
				editor.onClose(ed);
			}catch(ex){printToConsole(ex+" "+ex.trace);}
			return true;
		}//,
		//modes : { wysiwyg : 1, source : 1 }  
	};
	var buttonEditCmd = {
			exec: function (ed) {
				closeStarted = true;
				editorJavaDialog = true;
				if (ed.checkDirty() && !confirm('There are unsaved changes that will be lost. Continue?')) {
					return;
				}
				editorJavaDialog = false;
				ed.execCommand('requalityExit');
			}//,
			//modes : { wysiwyg : 1, source : 1 }  			
		};
	var preCmd = {
			exec:function(editor){
				editor.focus();
				editor.fire( 'saveSnapshot' );
		        editor.insertHtml( '<pre>' + escapeHtml(editor.getSelection().getSelectedText()) + '</pre>' );
				setTimeout( function() {
					editor.fire( 'saveSnapshot' );
				}, 0 );
			}
	}

	// Register the new plugin named "requality".
	CKEDITOR.plugins.add('requality', {
		icons: 'requalityexit,requalitysave,requalitypre',
		init: function (editor) {
			editor.addCommand('requalitySave', saveCmd);
			editor.addCommand('requalityExit', exitCmd);
			editor.addCommand('requalityButtonExit', buttonEditCmd);
			editor.addCommand('requalityPre', preCmd);
			editor.ui.addButton('RequalitySave', {
				label: 'Save changes and close editor',
				command: 'requalitySave'
			});
			editor.ui.addButton('RequalityExit', {
				label: 'Close editor discarding changes',
				command: 'requalityButtonExit'
			});
			editor.ui.addButton('RequalityPre', {
				label: 'Pre',
				command: 'requalityPre'
			});
		}
	});
})();
