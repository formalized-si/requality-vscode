handlers = [];

window.readOnlyChanged = function(newState){
  var info = document.getElementById("readOnlyInfo");
  if(info){
    info.className=!newState?"hiddenPoint":"";
  }
}	

handlers["init"] = function(op, current_instance, msg){
	loadTemplates();
	var editorName = msg['editorName'];
	var editMode = msg['editMode'];
	if(editMode)
      editMode = parseInt(editMode);
    else
      editMode = 0;
    if(!editorName)
      editorName = 'unieditor';
    window.readOnly = false;
    if(typeof msg['readOnly'] !== 'undefined' && msg['readOnly'] == 'true') 
      window.readOnly = true;
    window.readOnlyChanged(window.readOnly);
    window.readOnlyMode = editMode;
    window.editorName = editorName;
    
};
handlers["readOnlyChange"] = function(op, current_instance,msg){
  window.readOnly = false;
  if(typeof msg['readOnly'] !== 'undefined' && msg['readOnly'] == 'true') 
    window.readOnly = true;
  window.readOnlyChanged(window.readOnly);
};
handlers["sendImageLoaded"] = function (op, current_instance, msg){
  if(typeof window.imageUploaded !== "undefined"){
    window.imageUploaded(msg['path'], msg['error']);
  }
}
handlers["sendLoadNodeByUUIDResult"]=function(op, current_instance, msg){
	var uuid = msg['node'];
	var data = msg['data'];
	addToCache(data);
	if(typeof reactOnLoad !== "undefined"){
		var reacts = reactOnLoad[uuid];
		if(reacts)
			while(reacts.length>0)
				reactOnLoad[uuid].pop()();
	}
	
};
var templateLoadedActions=false;
handlers["updateNodeTemplates"] = function(op,current_instance, msg){
	$.each(msg["templates"], function (i, templ) {
		window.templateLabel[templ["type"]] = templ["templateLabel"];
	});
	window.templateLoaded = true;
	if(templateLoadedActions)
		templateLoadedActions();
}
window.reactOnSelectFileCall = null;
handlers["sendSelectFileDialogResult"] = function(op, current_instance, msg){
	var result = msg["result"];
	if(result == "")
		result = null;
	if(window.reactOnSelectFileCall!=null)
		window.reactOnSelectFileCall(result);
};
handlers["sendLoadNodeTreeByUUIDResult"] = function(op, current_instance, msg){
	var data = msg['data'];
	addToCache(data);
};

handlers["sendLoadNodeTreeByUUIDResultFinish"] = function(op, current_instance, msg){
	if(typeof reactOnLoadTree !== "undefined"){
		//TODO rewrite to load by packages
		var reacts = reactOnLoadTree;
		if(reacts)
			while(reacts.length>0)
				reactOnLoadTree.pop()();
	}

};
handlers["sendRedLinkSelectionReturn"] = function(op, current_instance, data){//node,nodes[]
	
};
handlers["sendNameCheckState"] = function(op, current_instance, data){//node
	var uuid = data["node"];
	if(data["state"])
		changeNodeAttr(uuid, '_name', data["newName"]);
	else{
		const fuuid = uuid;
		loadNodeThen(uuid, function(){
			var name = getName(getNodeByUUID(fuuid));
			window.attributeUpdate['_name'](fuuid,"_name",name,name);
		});
	}
//	if(checkName(fuuid,new_data))
//				changeNodeAttr(fuuid, '_name', new_data);
//			else{
//				var name = getName(getNodeByUUID(fuuid));
//				window.attributeUpdate['_name'](fuuid,"_name",name,name);
//			}
};
handlers["sendCheckChildsResult"] = function(op, current_instance, data){//node,nodes[],newNames
	//var node = data["node"];
	if(cachedDropTargets == null)
      cachedDropTargets = [];
	var cacheEnabged = cachedDropTargets;
	var nodes = data["nodes"];
	$.each(nodes, function (i, node) {
      if(cacheEnabged!=null)
	    cacheEnabged.push(node);
        var ignored = false;
        if(draggedNode){
		if(node == draggedNode)
                  ignored = true;
                else{
			var ndesc = getNodeByUUID(node);
			while(ndesc){
				if(ndesc.parent == draggedNode)
					ignored = true;
				if(ndesc.parent)
					ndesc = getNodeByUUID(ndesc.parent);
				else
					ndesc = null;
			}
		}
        }
        if(!ignored){
          var qnode = $('#node-' + node);
          if(qnode.hasClass('undropable'))
	        qnode.removeClass('undropable');
          if(qnode.hasClass('disabled'))
            qnode.removeClass('disabled');
        }
	});
};
handlers["oldRefresh"] = function(op, current_instance, data){//node,nodes[],newNames
	var uid = data["node"];
	onChangeNode(uid,true);
};
handlers["wrongLinkUpdateResult"] = function(op, current_instance, data){//euuid,sel, node,evid
	
};
handlers["sendNewSelectedNode"] = function(op, current_instance, data){//node
	setFocusByUUID(data["node"], false);
};
handlers["sendEvents"] = function(op, current_instance, data){//data
	handleEvents(data);
};

//TODO add handlers