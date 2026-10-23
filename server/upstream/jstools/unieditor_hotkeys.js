/*
Hotkey Manager is designed to assign different actions to hotkeys.
Version: 1.0
Author: Konstantin Vlasov

Synopsis:

	// Suppose we have two modes the application works: normal mode and text editor mode.

	// Define commands
	function copyItem(arg) {
		alert("Command copyItem was called with argument '" + arg + "'");
	}
	function copyText(arg) {
		alert("Command copyText was called with argument '" + arg + "'");
	}
	function pasteItem(arg) {
		alert("Command pasteItem was called with argument '" + arg + "'");
	}
	function pasteText(arg) {
		alert("Command pasteText was called with argument '" + arg + "'");
	}

	// Assign some hotkeys for different commands in both modes
	hotkeyManager.registerHotkeys({
		'Ctrl+C':    { global: copyItem,  editor: copyText  },
		'Ctrl+Ins':  { global: copyItem,  editor: copyText  },
		'Ctrl+V':    { global: pasteItem, editor: pasteText },
		'Shift+Ins': { global: pasteItem, editor: pasteText }
	});

	// Attach keyboard event handler
	document.addEventListener('keydown', function (event) {
		var activeElem = getActiveElement();   // Some external function
		var selection = getCurrentSelection(); // This too
		var namespace = editorActive ? 'editor' : 'global';
		hotkeyManager.execHotkeyAction(event, namespace, activeElem, selection);
	}, false);

	console.info(
		'Hotkeys assigned to copyText command:\n' +
		'globally: ' + hotkeyManager.command_hotkeys['global']['copyText'].join(', ') + '\n' +
		'in editor: ' + hotkeyManager.command_hotkeys['editor']['copyText'].join(', ')
	);
	// Outputs:
	//	Hotkeys assigned to copyText command:
	//	globally: 
	//	in editor: Ctrl+C, Ctrl+Ins
*/

// Object for manipulating hotkeys and corresponding commands
var hotkeyManager = (function () {
	// Each hotkey is translated into internal representation which is unique for the hotkey.
	// Then a handler is bound to this representation. When a key is pressed, the Event object
	// is also transformed into hotkey string, and thus the handler is retrieved.
	//
	// Internal representation looks like string in the format:
	//    <Shift><Ctrl><Alt>,<KeyCode>
	// <Shift>, <Ctrl> and <Alt> can be either '-' if the corresponding modifier is not set,
	// or 's', 'c', 'a', respectively.
	// <KeyCode> is the digital codes from the JavaScript keydown Event object.
	// Example 1: Ctrl+C         is translated into '-c-,67'
	// Example 2: Alt+Shift+Left is translated into 's-a,37'
	this.listeners = [];
	this.addListener=function(f){
		this.listeners.push(f);
	};
	this.fireEvent = function(mode, command){
		for(var l in this.listeners){
			try{
				this.listeners[l](mode,command);
			}
			catch(e){}
		}
	};
	///////////////////////////////////////////////////////////////////////	/////
	// Public members

	// List of command-to-hotkey associations
	this.command_hotkeys = {};

	// Stores the list of hotkeys handlers
	// Input parameters:
	//	hotkeys_map - Map of hotkeys in the form: 'hotkey': {namespace1: function, namespace2: function, ... }
	// Return value:
	//	none
	this.registerHotkeys = function (hotkeys_map) {
		for (var hotkey in hotkeys_map) {
			var parsed_hotkey = this.parseHotkey(hotkey);
			if (parsed_hotkey) {
				for (var namespace in hotkeys_map[hotkey]) {
					if (!hotkeys_map[hotkey][namespace])
						continue;
					if (!this.keyboard_shortcuts[namespace])
						this.keyboard_shortcuts[namespace] = {};
					this.keyboard_shortcuts[namespace][parsed_hotkey] = hotkeys_map[hotkey][namespace];
					// Put into command-to-hotkey array
					var command_name = hotkeys_map[hotkey][namespace].name;
					if (!this.command_hotkeys[namespace])
						this.command_hotkeys[namespace] = [];
					if (!this.command_hotkeys[namespace][command_name])
						this.command_hotkeys[namespace][command_name] = [];
					this.command_hotkeys[namespace][command_name].push(hotkey);
					if(namespace=="handlerHotMenu"){
						hideMenu();
					}
				}
			}
		}
	};

	// Searches and executes the action assigned to the hotkey pressed
	// Input parameters:
	//	event      - Event object produced by the 'keypressed' event
	//	namespace  - Namespace to look the hotkey assignment from
	//	targetElem - Target element to call the command for
	//	cmdArg     - Argument to pass into the command
	// Return value:
	//	true if hotkey action was found and run; false otherwise
	this.execHotkeyAction = function (event, namespace, targetElem, cmdArg) {
		if (!this.keyboard_shortcuts[namespace])
			return false;
		
		var action = this.keyboard_shortcuts[namespace][this.getParsedHotkey(event)];
		if (action) {
			event.preventDefault();
			callHandler(action, targetElem, cmdArg);
			fireEvent(namespace,cmdArg.name);
			return true;
		}
		return false;
	}


	////////////////////////////////////////////////////////////////////////////
	// Private members

	// Storage of hotkey assignments
	this.keyboard_shortcuts = {};

	// List of single key names associated with their codes (discovered by experimenting)
	this.single_keys = {
		'SPACE':      32,
		'BKSP':        8,
		'BKSPACE':     8,
		'BACKSPACE':   8,
		'TAB':         9,
		'ENTER':      13,
		'ESC':        27,
		'INS':        45,
		'INSERT':     45,
		'DEL':        46,
		'DELETE':     46,
		'HOME':       36,
		'END':        35,
		'PGUP':       33,
		'PAGEUP':     33,
		'PGDN':       34,
		'PGDOWN':     34,
		'PAGEDN':     34,
		'PAGEDOWN':   34,
		'UP':         38,
		'DOWN':       40,
		'LEFT':       37,
		'RIGHT':      39,
		'NUM+':      107,
		'NUM-':      109,
		'NUM*':      106,
		'NUM/':      111,
		'NUM.':      110,
		'\'':         192,
		'-':         109,
		'=':         107,
		'\\':        220,
		'[':         219,
		']':         221,
		';':          59,
		'\'':        222,
		',':         188,
		'.':         190,
		'/':         191
	};

	// Calculates the hotkey internal representation
	// Input parameters:
	//	event - Event object produced by the 'keypressed' event
	// Return value:
	//	String containing unified representation of a hotkey
	this.getParsedHotkey = function (event) {
		var str = '';
		str += (event.shiftKey ? 's' : '-');
		str += (event.ctrlKey ? 'c' : '-');
		str += (event.altKey ? 'a' : '-');
		str += ',' + event.keyCode;
		return str;
	}

	// Transforms user-friendly hotkey definition into internal representation
	// Input parameters:
	//	hotkey - String containing the user-friendly hotkey description (such as 'Ctrl+A')
	// Return value:
	//	String containing unified representation of a hotkey, or null if hotkey text failed to parse
	this.parseHotkey = function (hotkey) {
		// Emulation for the 'keypressed' event object
		var hotkey_event = {
			keyCode:  0,
			ctrlKey:  false,
			altKey:   false,
			shiftKey: false
		};
		var hotkey_txt = hotkey.toUpperCase();
		// Extract modifier keys
		if (hotkey_txt.indexOf('SHIFT+') != -1) {
			hotkey_txt = hotkey_txt.replace('SHIFT+', '');
			hotkey_event.shiftKey = true;
		}
		if (hotkey_txt.indexOf('CTRL+') != -1) {
			hotkey_txt = hotkey_txt.replace('CTRL+', '');
			hotkey_event.ctrlKey = true;
		}
		if (hotkey_txt.indexOf('ALT+') != -1) {
			hotkey_txt = hotkey_txt.replace('ALT+', '');
			hotkey_event.altKey = true;
		}
		// Only single key remained - check it
		if (this.single_keys[hotkey_txt]) {
			hotkey_event.keyCode = this.single_keys[hotkey_txt];
			return this.getParsedHotkey(hotkey_event);
		}
		else
			return null;
	}

	// Initialization: Add alphabetical and numeric one-letter keys (their keyCodes equal to ASCII codes)
	var single_keys_str = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
	for (var i = 0; i <= single_keys_str.length; ++i) {
		this.single_keys[single_keys_str.charAt(i)] = single_keys_str.charCodeAt(i);
	}
	// Also add F1..F12
	for (var i = 1; i <= 12; ++i) {
		this.single_keys['F' + i] = 111 + i;
	}
	// ...and NUM0..NUM9
	for (var i = 0; i <= 9; ++i) {
		this.single_keys['NUM' + i] = 96 + i;
	}

	return this;
})();
