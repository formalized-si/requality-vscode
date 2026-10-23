
// Assign default UniEditor hotkeys
// Note: JS cannot distinguish some keys like '=' and 'Num+'; '-' and 'Num-'; etc.
hotkeyManager.registerHotkeys({
	'Ctrl+Up':          { handlerGlobal: null,                       handlerCKEditor: editPrevious },
	'Ctrl+Down':        { handlerGlobal: null,                       handlerCKEditor: editNext     },
	'Esc':              { handlerGlobal: closeEditor,                 handlerCKEditor: closeEditor  },
	'Ctrl+S':           { handlerGlobal: null,                  handlerCKEditor: saveEditor   },
	'Up':               { handlerGlobal: focusPrevious,               handlerCKEditor: null         },
	'Down':             { handlerGlobal: focusNext,                   handlerCKEditor: null         },
	'Left':             { handlerGlobal: navigateLeft,                handlerCKEditor: null         },
	'Right':            { handlerGlobal: navigateRight,               handlerCKEditor: null         },
	'PageUp':           { handlerGlobal: pageUp,                      handlerCKEditor: null         },
	'PageDown':         { handlerGlobal: pageDown,                    handlerCKEditor: null         },
	'Home':             { handlerGlobal: focusFirst,                  handlerCKEditor: null         },
	'End':              { handlerGlobal: focusLast,                   handlerCKEditor: null         },
	'Ctrl+R':         	{ handlerGlobal: addChildRequirementConfirm,  handlerCKEditor: null         },
	'Ctrl+S':    		{ handlerGlobal: addSiblingRequirementConfirm,handlerCKEditor: null         },
	'Ctrl+J':         	{ handlerGlobal: addChildTextNodeConfirm,  handlerCKEditor: null         },
	'Ctrl+I':    		{ handlerGlobal: addSiblingTextNodeConfirm,handlerCKEditor: null         },
	'Alt+C':            { handlerGlobal: addComment,                  handlerCKEditor: null         },
	'Del':              { handlerGlobal: deleteNodeConfirm,           handlerCKEditor: null         },
	'Enter':            { handlerGlobal: editDescription,             handlerCKEditor: saveEditor   },
	'F2':               { handlerGlobal: editNameId,                  handlerCKEditor: editNameId   },
	'Alt+S':            { handlerGlobal: changeStatus,                handlerCKEditor: null         },
	'Num+':             { handlerGlobal: expandNode,                  handlerCKEditor: null         },
	'Num-':             { handlerGlobal: collapseNode,                handlerCKEditor: null         },
	'Ctrl+C':           { handlerGlobal: copyNodeToClipboard,         handlerCKEditor: null         },
	'Ctrl+E':        	{ handlerGlobal: editNode,				      handlerCKEditor: null         },
	'Ctrl+X':           { handlerGlobal: cutNodeToClipboard,          handlerCKEditor: cutNodeToClipboard         },
	'Ctrl+V':  		    { handlerGlobal: pasteNodeAsChild,       	  handlerCKEditor: null         },
	'Ctrl+Shift+V':     { handlerGlobal: pasteNodeAsSibling,          handlerCKEditor: null         },
	'Ctrl+Alt+V':       { handlerGlobal: pasteSubtreeAsChild,      	  handlerCKEditor: null         },
	'Ctrl+Alt+Shift+V': { handlerGlobal: pasteSubtreeAsSibling,       handlerCKEditor: null         },
    'Ctrl+Ins':         { handlerGlobal: copyNodeToClipboard,         handlerCKEditor: copyNodeToClipboard         },
    'Shift+Ins':        { handlerGlobal: pasteNodeAsSibling,          handlerCKEditor: pasteNodeAsChild         },
    'Ctrl+T':           { handlerGlobal: openTemplates,		          handlerCKEditor: null         },
    'Alt+T':    		{ handlerGlobal: callAddTest,		          handlerCKEditor: null         },
    'Alt+M':    		{ handlerGlobal: switchRequirementType,	      handlerCKEditor: null         }
});


// Custom toolbar that includes only those tools that are useful,
// plus two custom Requality buttons (Save, Exit).
CKEDITOR.config.toolbar_Requality = [
	{ name: 'document',    items : [ 'Sourcedialog','-', 'RequalitySave', 'RequalityExit' ] },
	
	{ name: 'clipboard',   items : [ 'SelectAll', 'Cut', 'Copy', 'Paste', 'PasteText', 'PasteFromWord', '-', 'Undo', 'Redo' ] },
//tools for linking, now includes only link to Test Purpose	
	{ name: 'linktp',      items : [ 'LinkTestPurpose' ] },
	'/',
	{ name: 'indents', items :['Outdent', 'Indent']},
	{ name: 'basicstyles', items : [ 'Bold', 'Italic', 'Underline', 'Strike', 'Subscript', 'Superscript', 'RequalityPre', '-', 'RemoveFormat', '-', 'NumberedList', 'BulletedList' ] },
	{ name: 'links',       items : [ 'SimpleImage', 'Link', 'Unlink', '-', 'Table', 'HorizontalRule', 'SpecialChar' ] }
];
constructNodeWrapperProcessor['Comment']=function (uuid) {
	return $('<div id="children-Comment-'+uuid+'" class="children children-Comment" />');
};

allowedTypes = ["Requirement","TextNode","Comment","TestStep","TestPurpose","TestProcedure"];
if(!window.attributeUpdate)
	window.attributeUpdate = [];
window.attributeUpdate['coverageStatus']=function(uuid,name,raw,val){
		window.attributeUpdate['_default'](uuid,"coverageStatus",raw,val);
		var node = getNodeByUUID(uuid);
		changeStatus(uuid,node.attributes._type.value,val,true);
		//onChangeNode(uuid);
	};
window.attributeUpdate['_type']=function(uuid, name, raw, val){
		var node = getNodeByUUID(uuid);
		window.attributeUpdate['_default'](uuid,name,raw,val);
		onChangeNode(uuid,false);
	};
window.attributeUpdate['_name']=function(uuid, name, raw, val){
		var node = getNodeByUUID(uuid);
		window.attributeUpdate['_default'](uuid,name,raw,val);
		//node.attributes._name = {'newvalue':val, 'value':raw};
		var id = getNameOrId(node);
		window.attributeUpdate['_default'](uuid,"_nameOrId",id['value'],id['newvalue']);	
	};
window.attributeUpdate['_default']=function(uuid, name, raw, val){
		var node = getNodeByUUID(uuid);
		if(raw==null && val == null)
			node.attributes[name] = null;
		else
			node.attributes[name]={'newvalue':val,'value':raw};
		var elem = document.getElementById(uuid+'-attribute-'+name);
		if(elem!=null && elem!==undefined){
			elem.innerHTML=(val!=null?val:"");
		}
		elem = document.getElementById(uuid+'-attribute-'+name+'-_raw');
		if(elem!=null && elem!==undefined){
			elem.innerHTML=(raw!=null?raw:"");
		}
	};
window.attributeUpdate['_description']=function(uuid,name,raw,val){
		window.attributeUpdate['_default'](uuid,"_description",raw,val);
		var des = getDescription(uuid);
		window.attributeUpdate['_default'](uuid,"_vdescription",des['value'],des['newvalue']);
	};
window.attributeUpdate['_textNodeType']=function(uuid,name,raw,val){
		var node = getNodeByUUID(uuid);
		window.attributeUpdate['_default'](uuid,name,raw,val);
		changeStatus(uuid,node.attributes._type.value,val,true);
		//TODO more clever update
		onChangeNode(uuid,false);
	};
window.attributeUpdate['_status']=function(uuid, name, raw, val){
  var node = getNodeByUUID(uuid);
  changeStatus(uuid,node.attributes._type.value,val,true);
}
window.attributeUpdate['coverageStatus']=function(uuid, name, raw, val){
  var node = getNodeByUUID(uuid);
  changeStatus(uuid,node.attributes._type.value,val,true);
}
window.attributeUpdate['_id']=function(uuid, name, raw, val){
		window.attributeUpdate['_default'](uuid,"_id",raw,val);
		var node = getNodeByUUID(uuid);
		var id = getNameOrId(node);
		window.attributeUpdate['_default'](uuid,"_nameOrId",id['value'],id['newvalue']);
	};
window.attributeUpdate['_locations']=function(uuid, name, raw, val){
		window.attributeUpdate['_default'](uuid,"_locations",raw,val);
		var nodeData = getNodeByUUID(uuid);
		formLocationDesc(nodeData);
	};

if(typeof constructNodeProcessor === "undefined")
	constructNodeProcessor = [];
// List of HTML block constructors for different types of nodes
constructNodeProcessor['Comment'] = function (nodeData, uuid) {
		// Fill in default values for missing attributes
		if (!nodeData.attributes._description || !nodeData.attributes._description.value)
			nodeData.attributes._description = { 'value': '&nbsp;' };
		nodeData.attributes._description.value = nodeData.attributes._description.value.replace(/  /g,"&nbsp;&nbsp;");//.replace(/\n/g, "<br/>");
		// Construct HTML block
		return $(
			'<div class="cmtwrapper" id="innerblock-' + uuid + '">' +
			  '<div class="comment supported-node dropTarget" id="node-' + uuid + '">' +
			    '<div class="cmtctrls nodectrls" style="float: right;">' +
			      '<div style="text-align: right;">' +
			        '<img src="images/menu.png" alt="[v]" />' +
			      '</div>' +
			      '<div class="menu" id="menu-'+uuid+'" style="display: none;">' +
			        constructMenu('Comment', uuid) +
			      '</div>' +
			    '</div>' +
			    '<div class="cmtdesc nodedesc reditable dragable cmtdesc-'+uuid+'" id="' + uuid + '-attribute-_description" contenteditable="false">' +
			      nodeData.attributes._description.value +
			    '</div>' +
			  '</div>' +
			'</div>'
		);
	};
	constructNodeProcessor['TextNode'] = function (nodeData, uuid){
		var extraAttrsString ='';
		for(var i=0;i<extraAttrs.length;i++){
			var attr = extraAttrs[i];
			var val = "";
			if(typeof nodeData.attributes[attr] != 'undefined')
				val = nodeData.attributes[attr].value;

			extraAttrsString+=attr+": <span id='"+uuid+"-attribute-"+attr+"' class='"+uuid+"-attribute-"+attr+"'>"+val+"</span><br/>";	
		}
		// Create the block for requirement
		var editable_prefix = (((readOnlyMode & EDIT_MODE_REQ_RO) == 0) ? '' : 'non');
		// Remove "Delete requirement" where we cannot delete node
		var candel = canDeleteNode(uuid);
		
		var editable_prefix2 = '';
        if (!candel && !editable_prefix)
            editable_prefix2 = 'non';
        formLocationDesc(nodeData);
		var des = getDescription(uuid);
		nodeData.attributes._vdescription = {'value':des['value'],'newvalue':des['newvalue']};
	
		// Fill in default values for missing attributes
		if (!nodeData.attributes._vdescription || !nodeData.attributes._vdescription.value)
			nodeData.attributes._vdescription = { 'value': '&nbsp;' };
		nodeData.attributes._vdescription.value = nodeData.attributes._vdescription.value.replace(/  /g,"&nbsp;&nbsp;");

		if (!nodeData.attributes._textNodeType || !nodeData.attributes._textNodeType.value)
			nodeData.attributes._textNodeType = { 'value': 'text' };
		// Construct HTML block
		var hname = "span";
		nodeData.attributes._nameOrId = getNameOrId(nodeData);
		var value = nodeData.attributes._vdescription.newvalue;
		var valueraw = nodeData.attributes._vdescription.value;
		
		var variable=' tndesc-'+uuid+'';
		var varid = uuid + '-attribute-_vdescription';
		if(nodeData.attributes._textNodeType.value=='header'){
			var luid = uuid;
			var nm = -2;
			while(luid!=null){
				luid = getParentUUID(luid);
				nm++;
			}
			valueraw = nodeData.attributes._nameOrId.value;
			value = nodeData.attributes._nameOrId.newvalue;
			hname = 'h'+nm;
			variable=' tnheader-'+uuid+'" id="' + uuid + '-attribute-_nameOrId';
			varid = uuid + '-attribute-_nameOrId';
		}
		/*
		var tr = $("<blockquote>", {
				'class':"indent nodewrapper",
				'id': "innerblock-" + uuid
			})	
				.append($("<div>",{
					'class':"textnode supported-node dropable",
					'id':'node-' + uuid
				})
					.append($("<table>",{
						'cellpadding':"0",
						'cellspacing':"0",
						'border':"0",
						'width':"100%"
					})
						.append($("<tr>"))
					)
				)
			);
		tr.append($("<td>",{
							valign:"top";
						})
							.append("<img>",{
								src:"images/" + getNodeStatus('TextNode',nodeData.attributes._textNodeType.value).icon,
								alt:nodeData.attributes._textNodeType.value,
								title:nodeData.attributes._textNodeType.value,
								class:"imgstatus",
								id:"status-" + uuid,
								onclick:execNodeCommand('TextNode',uuid, 'chstatus')
							}))
					);
		var newtd = ($("<td>",{
			width:"100%"
		})
			.append($("<"+hname+">",{
				style:"margin:0px;padding:0px;min-width:100%-;"
			}))
		);
			newtd.append($("<div>",{
				class:"tndesc nodedesc " + editable_prefix + "reditable dragable descr"+variable,
				id:varid	
			}).append($(value)));
		tr.append(newtd);
		
		newtd=($("<td>",{
			valign:"top"
		})
			.append($("<div>",{
				class:"textnodectrls nodectrls",
				style:"float: right;",
				bgcolor:"red"
			}))
		);
		newtd.append($("<div>",{
					style:"text-align: right;
				})
					.append($("<img>"),{
						src:"images/menu.png",
						alt:"[v]"
					}))
				);
		newtd.append($("div",{
			class:"menu",
			id:"menu-"+uuid,
			style:"display:none;" 
		}).append(constructMenu('TextNode', uuid)));
		tr.append(newtd);
		var newNode = tr;
		*/
		var newNode= $(
			'<blockquote class="indent nodewrapper" id="innerblock-' + uuid + '">' +
			  '<div class="textnode supported-node dropTarget" id="node-' + uuid + '">' +
				  '<table cellpadding="0" cellspacing="0" border="0" width="100%">' +
			      '<tr>' +
					(extraAttrsString.length==0?"":
				      	('<td class="extraAttrsC">'+
				      		'<span class="extraAttrs" id="extraAttrs-'+uuid+'">'+
					      		extraAttrsString+
					      	'</span>'+
				      	'</td>')) +
		            
		            '<td valign="top">' +
		            '<img src="images/' + getNodeStatus('TextNode',nodeData.attributes._textNodeType.value).icon + '" alt="' + nodeData.attributes._textNodeType.value + '" title="' + nodeData.attributes._textNodeType.value + '" class="imgstatus" id="status-' + uuid + '" class="imgstatus" onclick="javascript:execNodeCommand(\'TextNode\', \'' + uuid + '\', \'chstatus\');" />' +
				    '</td>' +
			        '<td width="100%">' +
			        	'<'+hname+' style="margin:0px;padding:0px;min-width:100%-;">'+
			        	'<div class="hiddenPoint" id="'+varid+'-_raw">'+valueraw+'</div>'+
			        	'<div class="tndesc nodedesc ' + editable_prefix + 'reditable dragable descr'+variable+'" id="'+varid+'">'+
				    	  value +
					     '</div>' +
					     '</'+hname+'>'+ 
				    '</td>' +
			        '<td valign="top">' +  
			        '<div class="textnodectrls nodectrls" style="float: right;" bgcolor = red>' +
				      '<div style="text-align: right;">' +
				        '<img src="images/menu.png" alt="[v]" />' +
				      '</div>' +
				      '<div class="menu"  id="menu-'+uuid+'" style="display: none;">' +
				        constructMenu('TextNode', uuid) +
				      '</div>' +
				    '</div>' +
			        '</td>' +
			      '</tr>' +
			    '</table>' +
			  '</div>' +
			'</blockquote>'
		);
		var dchild = $("<span/>");
		dchild.attr("class","dummychildren");
		newNode.append(dchild);
		/*
		var childrenNodes = {};
		for (var i in nodeData.children) {
			// Construct the next child subtree (for requirements) or table (for test purposes)
			var chldNode = constructTree(nodeData.children[i]);
			var chldType = chldNode.type;
			if ( synonim[chldType] )
				chldType = synonim[chldType];
			if (!childrenNodes[chldType]) {
				var wrapperConstructor = constructNodeWrapperProcessor[chldType];
				if (wrapperConstructor)
					childrenNodes[chldType] = wrapperConstructor(uuid);
				else
					childrenNodes[chldType] = $('<div />');
			}
			childrenNodes[chldType].append(chldNode);
		}
		var hasChildren=hasVisibleChildren(uuid);
		// Now append children nodes blocks to the current requirement node
		for (var i = 0; i < childrenNodesOrder.length; ++i) {
			var chldType = childrenNodesOrder[i];
			if (childrenNodes[chldType]) {
				newNode.append(childrenNodes[chldType]);
				if(chldType == "Generic" && hasChildren){
                    newNode.addClass('collapsable');
                    newNode.prepend($('<img src="images/collapse.png" alt="[&minus;]" class="plusminus" onclick="javascript:toggleChildren(\'' + uuid + '\');" />'));
				}
				// Or remove "Add child requirement" command from non-leaves
                if (chldType == 'TestPurpose')
                    $(newNode.find('.menu-item-addreqchld')[0]).remove();
			}
		}*/
		return newNode;
	};
	constructNodeProcessor['Requirement']= function (nodeData, uuid) {
		var extraAttrsString ='';
		for(var i=0;i<extraAttrs.length;i++){
			var attr = extraAttrs[i];
			var val = "";
			if(typeof nodeData.attributes[attr] != 'undefined')
				val = nodeData.attributes[attr].value;

			extraAttrsString+=attr+": <span id='"+uuid+"-attribute-"+attr+"' class='"+uuid+"-attribute-"+attr+"'>"+val+"</span><br/>";	
		}
	
		formLocationDesc(nodeData);
		var des = getDescription(uuid);
		nodeData.attributes._vdescription = {'value':des['value'],'newvalue':des['newvalue']};

		// Fill in default values for missing attributes
		if (!nodeData.attributes.coverageStatus || !nodeData.attributes.coverageStatus.value)
			nodeData.attributes.coverageStatus = { 'value': 'in process' };
		nodeData.attributes._vdescription.value = nodeData.attributes._vdescription.value.replace(/  /g,"&nbsp;&nbsp;"); 
		nodeData.attributes._nameOrId = getNameOrId(nodeData);
		//nodeData.attributes._nameOrId.newvalue=nodeData.attributes._nameOrId.value;
		// Create the block for requirement
		var editable_prefix = (((readOnlyMode & EDIT_MODE_REQ_RO) == 0) ? '' : 'non');
		// Remove "Delete requirement" where we cannot delete node
		var candel = canDeleteNode(uuid);
		var editable_prefix2 = '';
        if (!candel && !editable_prefix)
            editable_prefix2 = 'non';
        var newNode = $(
			'<blockquote class="indent nodewrapper" id="innerblock-' + uuid + '">' +
			  '<div class="requirement supported-node dropTarget" id="node-' + uuid + '">' +
			    '<table cellpadding="0" cellspacing="0" border="0" width="100%" class="reqname_wrap">' +
			      '<tr>' +
					(extraAttrsString.length==0?"":
				      	('<td class="extraAttrsC">'+
				      		'<span class="extraAttrs" id="extraAttrs-'+uuid+'">'+
					      		extraAttrsString+
					      	'</span>'+
				      	'</td>')) +
		            '<td>' +
		            '<img src="images/' + getNodeStatus('Requirement',nodeData.attributes.coverageStatus.value).icon + '" alt="' + nodeData.attributes.coverageStatus.value + '" title="' + nodeData.attributes.coverageStatus.value + '" class="imgstatus" id="status-' + uuid + '" class="imgstatus" onclick="javascript:execNodeCommand(\'Requirement\', \'' + uuid + '\', \'chstatus\');" />' +
		            '</td>' +
			        '<td width="100%">' +
			          '<div class="hiddenPoint" id = "'+uuid+'-attribute-_nameOrId-_raw">'+nodeData.attributes._nameOrId.value+"</div>"+
			          '<div class="reqname nodetitle ' + editable_prefix + 'renamable ' + getNodeStatus('Requirement',nodeData.attributes.coverageStatus.value).className + ' dragable reqname-'+uuid+'" id="' + uuid + '-attribute-_nameOrId" contenteditable="false">' +
			            nodeData.attributes._nameOrId.newvalue +
			          '</div>' +
			        '</td>' +
			        '<td>' +
			          '<div class="reqctrls nodectrls">' +
			            '<div style="text-align: right;">' +
			              '<img src="images/menu.png" alt="[v]" />' +
			            '</div>' +
			            '<div class="menu"  id="menu-'+uuid+'" style="display: none;">' +
			              constructMenu('Requirement', uuid) +
			            '</div>' +
			          '</div>' +
			        '</td>' +
			      '</tr>' +
			    '</table>' +
			    '<div class="hiddenPoint" id="' + uuid + '-attribute-_vdescription-_raw">' +
			      nodeData.attributes._vdescription.value +
			    '</div>' +
			    '<div class="reqdesc nodedesc ' + editable_prefix + 'reditable reqdesc-'+uuid+'" id="' + uuid + '-attribute-_vdescription" contenteditable="false">' +
			      nodeData.attributes._vdescription.newvalue +
			    '</div>' +
			  '</div>' +
			'</blockquote>'
		);
	
		var dchild = $("<span/>");
		dchild.attr("class","dummychildren");
		newNode.append(dchild);
		/*
		// Enumerate all children objects and recursively construct the rest of the tree
		var childrenNodes = {};
		for (var i in nodeData.children) {
			// Construct the next child subtree (for requirements) or table (for test purposes)
			var chldNode = constructTree(nodeData.children[i]);
			var chldType = chldNode.type;
			if ( synonim[chldType] )
				chldType = synonim[chldType];
			if (!childrenNodes[chldType]) {
				var wrapperConstructor = constructNodeWrapperProcessor[chldType];
				if (wrapperConstructor)
					childrenNodes[chldType] = wrapperConstructor(uuid);
				else
					childrenNodes[chldType] = $('<div />');
			}
			childrenNodes[chldType].append(chldNode);
		}
		// Now append children nodes blocks to the current requirement node
		var hasChildren=hasVisibleChildren(uuid);
		for (var i = 0; i < childrenNodesOrder.length; ++i) {
			var chldType = childrenNodesOrder[i];
			if (childrenNodes[chldType]) {
				newNode.append(childrenNodes[chldType]);
				if (chldType == "Generic" && hasChildren) {
                    newNode.addClass('collapsable');
                    // Remove "Add Test Purpose" command from non-leaves
					newNode.prepend($('<img src="images/collapse.png" alt="[&minus;]" class="plusminus" onclick="javascript:toggleChildren(\'' + uuid + '\');" />'));
				}
				// Or remove "Add child requirement" command from non-leaves
                if (chldType == 'TestPurpose')
                    $(newNode.find('.menu-item-addreqchld')[0]).remove();
			}
		}
        // Remove "Add sibling requirement" command from root node
		var rootNode = getStartNode();
		*/
		return newNode;
	};
	constructNodeProcessor["VirtualNode"]=function(nodeData,uuid){
		// Create the block for requirement
		var editable_prefix = (((readOnlyMode & EDIT_MODE_REQ_RO) == 0) ? '' : 'non');
		// Remove "Delete requirement" where we cannot delete node
		var candel = canEditNode(uuid);
		var editable_prefix2 = '';
        if (!candel && !editable_prefix)
            editable_prefix2 = 'non';
        var newNode = $(
			  '<div class="nodewrapper" id="innerblock-'+uuid+'"><div class="instancer supported-node" id="node-' + uuid + '">' +
			  '</div></div>'
		);
	
		var dchild = $("<span/>");
		dchild.attr("class","dummychildren");
		newNode.append(dchild);

		// Enumerate all children objects and recursively construct the rest of the tree
		/*var childrenNodes = {};
		for (var i in nodeData.children) {
			// Construct the next child subtree (for requirements) or table (for test purposes)
			var chldNode = constructTree(nodeData.children[i]);
			var chldType = chldNode.type;
			if ( synonim[chldType] )
				chldType = synonim[chldType];
			if (!childrenNodes[chldType]) {
				var wrapperConstructor = constructNodeWrapperProcessor[chldType];
				if (wrapperConstructor)
					childrenNodes[chldType] = wrapperConstructor(uuid);
				else
					childrenNodes[chldType] = $('<div />');
			}
			childrenNodes[chldType].append(chldNode);
		}
		// Now append children nodes blocks to the current requirement node
		for (var i = 0; i < childrenNodesOrder.length; ++i) {
			var chldType = childrenNodesOrder[i];
			if (childrenNodes[chldType]) 
				newNode.append(childrenNodes[chldType]);
		}*/
        // Remove "Add sibling requirement" command from root node
		return newNode;
	};

constructNodeChildren['Requirement'] = constructNodeChildren['TextNode'] = function (nodeData) {
	var n = constructNodeChildren['_default'](nodeData);
	for (var i = 0; i < nodeData.children.length; ++i) {
		var chldType = getNodeByUUID(nodeData.children[i]).attributes._type.value;
		if (chldType == 'TestPurpose')
			$($("#block-"+nodeData.uuid).find('.menu-item-addreqchld')[0]).remove();
		if (chldType == 'Requirement')
			$($("#block-"+nodeData.uuid).find('.menu-item-addtp')[0]).remove();
	}
	return n;
};