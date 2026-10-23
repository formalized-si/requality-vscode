var menuHidden = true;
window.MENU_HOTKEYS = false; 

function constructHotMenu(uuid) {
	var rmode = readOnlyMode || getReadOnlyModeOnNode(uuid);
	var node_type = getType(uuid); 
	var menu = '';
	var menu_commands = node_commands[node_type],globalCommands= node_commands["global"];
	if (!menu_commands && !globalCommands)
		return '';
	var allCommands = hotkeyManager.command_hotkeys['handlerHotMenu'];
	for (var ind in menu_commands) {
		var menuPoint = menu_commands[ind];
		var name = menuPoint.handler.name;
		var label = menuPoint.name;
		if(allCommands.indexOf(name) == -1)
			allCommands.push(name);
	}
	for (var ind in allCommands) {
	    var hanlerId = allCommands[ind];
		menu_commands = node_commands[node_type]
		var cmdId=hanlerId;
		var cmdName = hanlerId;
		var label = cmdName;
		var className = 'menu-item';
		var found = false;
		var crType = null;
		var enFunc = null;
		for(var menuId in menu_commands){

			if(menu_commands[menuId].handler.name==hanlerId){
				label=menu_commands[menuId].name;
				cmdId = menuId;
				found = true;
				crType = menu_commands[menuId].createdType;
				enFunc = menu_commands[menuId].enableFunc;
			}
		}
		if(!found)
			for(var menuId in globalCommands)
				if(globalCommands[menuId].handler.name==hanlerId){
					//hack
					menu_commands = globalCommands;
					label=globalCommands[menuId].name;
					cmdId = menuId;
					found = true;
					crType = globalCommands[menuId].createdType;
		}
		if(!found)
			continue;
        if(window.readOnly || (typeof cmdId !== 'undefined') && (isSuitable[cmdId]) && !isSuitable[cmdId](uuid))
          continue;
		if(typeof enFunc === 'undefined' || enFunc == null)
		  enFunc = true;
        if (typeof enFunc === 'function') {
          enFunc = enFunc(uuid);
        }
        if(!enFunc)
		  continue;
		try{
			if((isSuitable[hanlerId]) && !isSuitable[hanlerId](uuid))
				continue;
		}catch(e){
			alert(e);
		}
		if(crType){
			var templ = getActiveTemplateLabel(uuid,crType);
			if(templ){
				label+="[template-"+menu_commands[cmdId].createdType+"]";
			}
		}
		var hotkey = '';
		var key = hotkeyManager.command_hotkeys['handlerHotMenu'][hanlerId];
		if(typeof key!="undefined")
		  hotkey = '<span style="color: #606060;">[' + hotkeyManager.command_hotkeys['handlerHotMenu'][hanlerId].join(', ') + ']</span>';
		else
		  hotkey = '';
		
		if ((cmdId in menu_commands) && (menu_commands[cmdId].readOnlyMode & rmode) != 0)
			className = 'menu-item-disabled';
		var image = '';
		if((cmdId in menu_commands) && menu_commands[cmdId].image)
			image = '<img src="images/' + menu_commands[cmdId].image + '" alt="' + menu_commands[cmdId].imageAlt + '" />';
		menu += '<div data-uuid="'+uuid+'" class="' + className + ' menu-item-' + cmdId + '" onclick="javascript:hideMenu();execNodeCommand(\'' + node_type
		 + '\', \'' + uuid + '\', \'' + cmdId + '\');"><table><tr><td width="20">'+image+'</td><td><div class="'
		 + className +'-'+ cmdId + '-name">' + label + '</div></td><td align="right"><img src="images/empty.png" alt="" width="10" height="1" />'
		 + hotkey + '</td></tr></table></div>';
	}
	return menu;
}
function showMenuAlt(){
	showMenu();
}
function showMenu(){
//	if(MENU_HOTKEYS)
//		return;
	window.MENU_HOTKEYS = "focus";
	var menu_caller = $("#menuhotctrls");
	var menu_caller_dims = menu_caller.dimensions();
	var container;
	var hmenu = menu_caller.find('#hotkey-menu');
	hmenu.html(constructHotMenu(selectedNode));
	
	menuHidden = false;
	hmenu.css({
		position: 'absolute',
		top: menu_caller_dims.top-menu_caller.parent().dimensions().top-6 + 'px',
		left: (menu_caller_dims.left-hmenu.width()+menu_caller.width()) + 'px',
		width: 'auto',
		height: 'auto',
		zIndex: 1000
	}).show();
	return hmenu;
}
function hotKeyAddChildRequirementConfirm(uuid){
	addChildRequirementConfirm(uuid);
	hideMenu();
}
function hideMenuAlt(){
	hideMenu();
}
function hideMenu(){
//	if(MENU_HOTKEYS)
//		return;
    window.MENU_HOTKEYS = false;
	menuHidden = true;
	var menu_caller = $("#menuhotctrls");
	var menu_glob = $('#hotkey-menu');
	$('#hotkey-menu *').detach();
	menu_glob.hide();
	var menus = $(".menu *");
	menus.detach();
	$(".menu").mouseleave();
}
function callHandler(action, targetElem, uuid){
	if(isSuitable[action.name]&&!isSuitable[action.name](uuid))
		return;
	action.call(targetElem, uuid);
}
$(function () {
	hotkeyManager.addListener(function(namespace,command){
		if(namespace == "handlerHotMenu")
			hideMenu();
	});
	node_commands['global']['hideMenu']={
		image: 'exit.png',
		imageAlt: '[Cl]',
		menu: true,
		name: 'Close menu',
		handler: hideMenu,
		readOnlyMode:0
	};
	isSuitable['hideMenu'] = function(){return true};
	$( "#toolsPanel").
	prepend(
			'<span id="menuhotctrls" style="float: right; margin-right:16px;" data-uuid="focus">'+
				'<span class="toolsButton" >' +
					'<img src="images/menu-alt.png" alt="[v]"/>'+
				'</span><span id="hotkey-menu" class="hotkey-menu htmenuctrls" style="position:absolute;display:none;width:200px;"/>' +
		    '</span>' );

	hotkeyManager.registerHotkeys({
		'T':         	  	{ handlerGlobal: null,		             handlerCKEditor: null, 			handlerHotMenu: openTemplates},
		'Alt+T':            { handlerGlobal: null,           		 handlerCKEditor: null, 			handlerHotMenu: callAddTest },
		'Del':         	  	{ handlerGlobal: null,		             handlerCKEditor: null, 			handlerHotMenu: deleteNodeConfirm},
		'E':         	  	{ handlerGlobal: null,		             handlerCKEditor: null, 			handlerHotMenu: editNode},
		'A':         	  	{ handlerGlobal: null,		             handlerCKEditor: null, 			handlerHotMenu: changeStatus},
		'Ctrl+D':           { handlerGlobal: showMenuAlt,            handlerCKEditor: null, 			handlerHotMenu: null },
		'S':         	  	{ handlerGlobal: null,		             handlerCKEditor: null, 			handlerHotMenu: addSiblingRequirementConfirm},
		'R':         	  	{ handlerGlobal: null,		             handlerCKEditor: null, 			handlerHotMenu: addChildRequirementConfirm},
		'I':         	  	{ handlerGlobal: null,		             handlerCKEditor: null, 			handlerHotMenu: addSiblingTextNodeConfirm},
		'J':         	  	{ handlerGlobal: null,		             handlerCKEditor: null, 			handlerHotMenu: addChildTextNodeConfirm},
		'M':         	  	{ handlerGlobal: null,		             handlerCKEditor: null, 			handlerHotMenu: switchRequirementType},
		'C':         	  	{ handlerGlobal: null,		             handlerCKEditor: null, 			handlerHotMenu: addComment},
		'Esc':           	{ handlerGlobal: hideMenu,               handlerCKEditor: saveEditor, 			handlerHotMenu: hideMenuAlt}
	});
	hotkeymode['handlerHotMenu']=function(){
		return !menuHidden || !!window.MENU_HOTKEYS;
	};
});
$(document).on('mouseenter','#menuhotctrls', function () {
	var hmenu = showMenu('menuhotctrls');	
	// If we expand the existing element it will stretch the table cell with
	// the test purpose ID or requirement name. To avoid this we create a copy of the
	// drop-down menu, position it as "absolute" above the old one and expand.
	// Calculate how much the menu should be stretched.
	// jQuery Actual Plugin is used for getting size of a hidden element.
	window.MENU_HOTKEYS = hmenu["data-uuid"];
	if(typeof window.MENU_HOTKEYS == "undefined")
	  window.MENU_HOTKEYS = "focus";
	var inside_menu = true;
	hmenu.mouseleave(function () {
		// Protect from double-entering
		if (inside_menu) {
			inside_menu = false;
			$(document).unbind('mousemove.hotkey-menu');
			// When mouse goes away restore normal classes for "item-under-cursor" highlighting
			// ...and animate collapsing the menu
			hideMenu('menuhotctrls');
			// Unbind mousemove listener
			
		}
	});
})
