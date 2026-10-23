var menuconditions=new Array();
var menutemplate=new Array();
var isnewlocation=new Array();
function createLocationReqFromDialogPoint(e){
	if(event.which!=1)
		return;
	try{
		//TODO add dialog with menu
//	  markup.create_location("");
//	  //cmenu.classList.remove("menuvisible");
//	  var cmenu = document.getElementById("req-context-menu");
//      cmenu.classList.remove("menuvisible");
	}catch(r){
		alert(r+" "+r.stack);
	}
}
function createLocationToExistSel(e){
	if(event.which!=1)
		return;
	try{
		
      //window.nextId, window.requirement, window.requirementUUID
	  //loc["fragments"], loc["fragmentIds"], loc["seleciton"];
	  var loc = window.markup.create_location(window.sel);
	  $.each(JSON.parse(loc["fragmentIds"]), function (i, val) {
	  	window.markup.remove_fragment(val);
	  });
	  createLocationLinkRequirement(loc["fragments"], loc["fragmentIds"], loc["seleciton"], window.requirementUUID); 
	  //cmenu.classList.remove("menuvisible");
	  hideMenu();
	}catch(r){
		alert(r+" "+r.stack);
	}
}

function hideMenu(){
  var cmenu = document.getElementById("req-context-menu");
  cmenu.classList.remove("menuvisible");
}

function createLocationNewReqId(e){
	if(event.which!=1)
		return;
	try{
	  var loc = window.markup.create_location(window.sel);
	  $.each(JSON.parse(loc["fragmentIds"]), function (i, val) {
	  	window.markup.remove_fragment(val);
	  });
      createLocationCreateRequirement(loc["fragments"], loc["fragmentIds"], loc["seleciton"], window.requirementUUID, window.nextId, null); 
	  
	  //
      hideMenu();
	}catch(r){
		alert(r+" "+r.stack);
	}
}

function createLocationNewReqName(e){
	if(event.which!=1)
		return;
	try{
	  var loc = window.markup.create_location(window.sel);
	  $.each(JSON.parse(loc["fragmentIds"]), function (i, val) {
	  	window.markup.remove_fragment(val);
	  });
      createLocationCreateRequirement(loc["fragments"], loc["fragmentIds"], loc["seleciton"], window.requirementUUID, window.nextId,savedText); 
	  
	  //
	  hideMenu();
      
	}catch(r){
		alert(r+" "+r.stack);
	}
}
function linkLocationReqFromDialogPoint(e){
	if(event.which!=1)
		return;
	try{
		//TODO add dialog with menu
//	  markup.create_location("");
//	  //
//	  hideMenu();
//      
	}catch(r){
		alert(r+" "+r.stack);
	}
}
function linkLocationToExistSel(e){
	if(event.which!=1)
		return;
	try{
		var loc = window.markup.get_checked_fragment();
		if(!loc)
			return;
	  linkLocationLinkRequirement(loc['location'], window.requirementUUID);
	  //
	  hideMenu();
      
	}catch(r){
		alert(r+" "+r.stack);
	}
}



function linkLocationNewReqId(e){
	if(event.which!=1)
		return;
	try{
	  var loc = window.markup.get_checked_fragment();
      if(!loc)
		return;
	  linkLocationCreateRequirement(loc['location'], window.requirementUUID, window.nextId, null);
	  
	  //
	  hideMenu();
      
	}catch(r){
		alert(r+" "+r.stack);
	}
}

function linkLocationNewReqName(e){
	if(event.which!=1)
		return;
	try{
		var loc = window.markup.get_checked_fragment();
      if(!loc)
		return;
	  linkLocationCreateRequirement(loc['location'], window.requirementUUID, window.nextId, markup.get_checked_fragment_text());
	  
	  //
	  hideMenu();
      
	}catch(r){
		alert(r+" "+r.stack);
	}
}
function removeLocationMenuPoint(e){
	if(e.which!=1)
		return;
	let loc = window.markup.get_checked_fragment();
	if(loc == null)
		return;
	let id = loc['location'];
	if(id == null)
		return;
    try{
	  removeLocation(id);
  //      let current_instance = markup;
	    //e.preventDefault();

//		let frags = current_instance.locations[id]['fragments'];
//    	if (!frags) return err_notify("There are no fragments to delete!");
//		
//        // Remove fragments first
//		$.each(current_instance.highlighter.highlights, function (i, hl_obj) {
//			if(loc['id']==hl_obj['id'])
//   				removeFragmentCall(getLocation(), current_instance.fragment_location['' + hl_obj['id']], hl_obj['characterRange']['start']+"|"+hl_obj['characterRange']['end']);
//        });
//
        hideMenu();
        
	}
	catch(e){
		alert(e+" "+e.stack);
	}
    //$('#html_content_loader').removeClass('active');
  }

  function normalizePozition (contextMenu, mouseX, mouseY) {
    const scope = document.body;
    // ? compute what is the mouse position relative to the container element (scope)
    let ret = scope.getBoundingClientRect();
    
    let swidth = window.innerWidth;
    let sheight = window.innerHeight;
    scopeOffsetX = ret.left;
    scopeOffsetY = ret.top;
	
    scopeOffsetX = scopeOffsetX < 0 ? 0 : scopeOffsetX;
    scopeOffsetY = scopeOffsetY < 0 ? 0 : scopeOffsetY;
    //TODO test it
    const scopeX = mouseX - scopeOffsetX;
    const scopeY = mouseY - scopeOffsetY;
    var hei = contextMenu.clientHeight;
    if(hei == 0)
    	hei = 300;

    // ? check if the element will go out of bounds
    const rightPosition = scopeX + contextMenu.clientWidth,
    	  bottomPosition = scopeY + hei;
    const outOfBoundsOnX =
            rightPosition > swidth || rightPosition > ret.right;

    const outOfBoundsOnY =
            bottomPosition > sheight || bottomPosition > ret.bottom;

    let normalizedX = mouseX;
    let normalizedY = mouseY;

    // ? normalize on X
    if (outOfBoundsOnX) {
      normalizedX =
        mouseX - contextMenu.clientWidth;
    }

    // ? normalize on Y
    if (outOfBoundsOnY) {
      normalizedY =
        mouseY - hei;
    }

    return [ normalizedX, normalizedY ];
  };
  function getRangySelTags(){
	return window.sel.toString().replace(/\r/g, "").trim();
  }
  function getRangySel(){
	return window.sel.toString().replace(/\r/g, "").replace( /(<([^>]+)>)/ig, '').trim();
  }
function initF(){
	
  const contextMenu = document.createElement("div");
  contextMenu.id = "req-context-menu";
  var menupoint;
  
  var hasSelectedLocation=function(){
    return window.markup.get_checked_fragment()!=null;
  };

  var noNodesInSelection=function(){
	var text = getRangySel();
	if(text == null || text.length == 0)
		return false;
    if(getNodesInRange(window.sel).length == 0)
    	return true;//document.getElementsByClassName(window.markup.fragment_active_class).length==0;
    return false; 
  };
	//new location

/*
  menupoint = document.createElement("div");
  menupoint.id = "createreqdialog";
  isnewlocation[menupoint.id] = true; 
  menuconditions[menupoint.id] = function(e){
	return noNodesInSelection() && window.tagText && window.tagText.length>0;
  };
  menupoint.classList.add("item");
  menutemplate[menupoint.id] = "Create new Location for Requirement...";
  $(menupoint).mousedown(createLocationReqFromDialogPoint);
  contextMenu.appendChild(menupoint);  
*/

  menupoint = document.createElement("div");
  menupoint.id = "createselreqtosel";
  isnewlocation[menupoint.id] = true;
  menuconditions[menupoint.id] = function(e){
	return noNodesInSelection() && window.tagText && window.tagText.length>0;
  };
  menupoint.classList.add("item");
  menutemplate[menupoint.id] = "Create new Location for Requirement: \"{REQ}\"";
  $(menupoint).mousedown(createLocationToExistSel);
  contextMenu.appendChild(menupoint);  

  menupoint = document.createElement("div");
  menupoint.id = "createnewidreqloc";
  isnewlocation[menupoint.id] = true;
  menuconditions[menupoint.id] = function(e){
	return noNodesInSelection() && window.tagText && window.tagText.length>0;
  };
  menupoint.classList.add("item");
  menutemplate[menupoint.id] = "Create new Location for New Requirement: \"{REQ}/{ID}\"";
  $(menupoint).mousedown(createLocationNewReqId);
  contextMenu.appendChild(menupoint);  


  menupoint = document.createElement("div");
  menupoint.id = "createnewnamereqloc";
  isnewlocation[menupoint.id] = true;
  menuconditions[menupoint.id] = function(e){
	var selText = getRangySel();
	savedText = selText;
	return noNodesInSelection() && selText && selText.length<128;
  };
  menupoint.classList.add("req-menu-item");
  menutemplate[menupoint.id] = "Create new Location for New Requirement: \"{REQ}/{NAME}\"";
  $(menupoint).mousedown(createLocationNewReqName);
  contextMenu.appendChild(menupoint);

	//exist location

/*
  menupoint = document.createElement("div");
  menupoint.id = "linkreqdialog";
  isnewlocation[menupoint.id] = false;
  menuconditions[menupoint.id] = function(e){
	return hasSelectedLocation() && true;
  };
  menupoint.classList.add("req-menu-item");
  menutemplate[menupoint.id] = "Link selected Location to Requirement...";
  $(menupoint).mousedown(linkLocationReqFromDialogPoint);
  contextMenu.appendChild(menupoint);  */


  menupoint = document.createElement("div");
  menupoint.id = "linkselreqtosel";
  isnewlocation[menupoint.id] = false;
  menuconditions[menupoint.id] = function(e){
	return hasSelectedLocation() && true;
  };
  menupoint.classList.add("req-menu-item");
  menutemplate[menupoint.id] = "Link selected Location to Requirement: \"{REQ}\"";
  $(menupoint).mousedown(linkLocationToExistSel);
  contextMenu.appendChild(menupoint);  

  menupoint = document.createElement("div");
  menupoint.id = "linknewidreqloc";
  isnewlocation[menupoint.id] = false;
  menuconditions[menupoint.id] = function(e){
	return hasSelectedLocation() && true;
  };
  menupoint.classList.add("req-menu-item");
  menutemplate[menupoint.id] = "Link selected Location to New Requirement: \"{REQ}/{ID}\"";
  $(menupoint).mousedown(linkLocationNewReqId);
  contextMenu.appendChild(menupoint);  

  menupoint = document.createElement("div");
  menupoint.id = "linknewnamereqloc";
  isnewlocation[menupoint.id] = false;
  menuconditions[menupoint.id] = function(e){
	var selText = window.markup.get_checked_fragment_text();
	return hasSelectedLocation() && selText &&  selText.length<128;
  };
  menupoint.classList.add("req-menu-item");
  menutemplate[menupoint.id] = "Link selected Location to New Requirement: \"{REQ}/{NAME}\"";
  $(menupoint).mousedown(linkLocationNewReqName);
  contextMenu.appendChild(menupoint);
  
  menupoint = document.createElement("div");
  menupoint.id = "removeitem";
  isnewlocation[menupoint.id] = false;
  menuconditions[menupoint.id] = hasSelectedLocation;
  menupoint.classList.add("req-req-menu-item");
  menutemplate[menupoint.id] = "Remove selected location";
  $(menupoint).mousedown(removeLocationMenuPoint);
  contextMenu.appendChild(menupoint);  

  var roPoint;
  menupoint = document.createElement("div");
  roPoint = menupoint.id = "readOnlyInfo";
  menupoint.classList.add("req-menu-item");
  menuconditions[menupoint.id] = function(e){
    return window.readOnly;
  };
  menutemplate[menupoint.id] = "Some actions are performed, markup editor is in read-only mode.";
  contextMenu.appendChild(menupoint);
  
  document.body.appendChild(contextMenu);
      //const contextMenu = document.getElementById("req-context-menu");
  const scope = document.querySelector("body");
  
  var menuList = function(event){
    if(isInTool()){//Additional debug possibility
      if(typeof window.NO_MENU != "undefined" && window.NO_MENU)
        return;
    }
    window.MENU_ACTIVE=true;
	window.sel = rangy.getSelection();//preinit selection for create location method
    window.tagText = getRangySelTags();
    if(window.tagText && window.tagText.length!=0){
	  deactivateLocations();
	}

	if(typeof event.preventDefault!="undefined")
		event.preventDefault();
	event.returnValue = false;
	
    const mouseX=event.clientX, mouseY=event.clientY;

    contextMenu.classList.remove("menuvisible");
    const ret = normalizePozition(contextMenu, mouseX, mouseY);
	const normalizedX=ret[0], normalizedY =ret[1];

	contextMenu.setAttribute('style','top:'+normalizedY+'px;left:'+normalizedX+'px');
//    contextMenu.style.top = normalizedY+'px';
//    contextMenu.style.left = normalizedX+'px';

    try{
	    contextMenu.classList.add("menuvisible");
		var createname = getRangySel();
		var linkname = "ERROR";
		
		if(hasSelectedLocation()){
			linkname = window.markup.get_checked_fragment_text();
		}
		for (var menuid in menuconditions) {
	    	const condition = menuconditions[menuid];

  			var newLoc = isnewlocation[menuid];
			if(!newLoc)
				newLoc = false;
			var point = $("#"+menuid);
			var name = newLoc?createname:linkname;
			var text = !menutemplate[menuid]?"ERROR":menutemplate[menuid];
			//alert(text);
			if(!window.requirement)
				window.requirement = "Requirements";
			point.text(text.replace("{REQ}",window.requirement).replace("{NAME}",name).replace("{ID}",window.nextId)); 
			try{
				if(menuid!=roPoint && window.readOnly || !condition()){//if menu condition return false
					point.removeClass("req-menu-item");
					point.addClass("hiddenPoint");
				}else{
					point.removeClass("hiddenPoint");
					point.addClass("req-menu-item");
				}
			}catch(e){
				alert(e);
			}
		};
			
	}catch(e){
		alert(e+" "+e.stack);
	}
    
  };
  scope.addEventListener("contextmenu", menuList);

  scope.addEventListener("click", function(e) {
    if(contextMenu != 'undefined' && contextMenu.classList.contains("menuvisible")){
      e.preventDefault();
      e.stopPropagation();
      // ? close the menu if the user clicks outside of it
      if (e.target.offsetParent != contextMenu) {
        contextMenu.classList.remove("menuvisible");
      }
    }
    window.MENU_ACTIVE=false;
  });
}
//if(typeof inTool!='undefined' && inTool){
//  initF();
//}else

$( document ).ready(function() {
  initF();
});