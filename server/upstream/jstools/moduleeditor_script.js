// <![CDATA[
            

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
	{ name: 'document',    items : [ 'Sourcedialog','-','RequalitySave', 'RequalityExit' ] },
	{ name: 'clipboard',   items : [ 'SelectAll', 'Cut', 'Copy', 'Paste', 'PasteText', 'PasteFromWord', '-', 'Undo', 'Redo' ] },	'/',
	{ name: 'indents', items :['Outdent', 'Indent']},
	{ name: 'basicstyles', items : [ 'Bold', 'Italic', 'Underline', 'Strike', 'Subscript', 'Superscript', 'RequalityPre', '-', 'RemoveFormat', '-', 'NumberedList', 'BulletedList' ] },
	{ name: 'links',       items : [ 'SimpleImage', 'Link', 'Unlink', '-', 'Table', 'HorizontalRule', 'SpecialChar' ] },
];
function calcSize(deep){
	var size = 200;
	if(deep>=8){
		var add = deep - 8;
		deep = 8;
		size-=add*7;
	}
	size-=deep*15;
	return size;
}
function getPath(node)
{
	return node.getPath();
}
allowedTypes = ["Requirement","TextNode"];
if(!window.attributeUpdate)
  window.attributeUpdate = [];
window.attributeUpdate['_id']=function(uuid, name, raw, val){
		//alert(uuid+" "+raw);
		window.attributeUpdate['_default'](uuid,"_id",raw,val);
		var node = getNodeByUUID(uuid);
		var id = getNameOrId(node);
		window.attributeUpdate['_default'](uuid,"_nameOrId",id['value'],id['newvalue']);
	};
window.attributeUpdate['_type']=function(uuid, name, raw, val){
		var node = getNodeByUUID(uuid);
		node.attributes._type={'value':val};
		onChangeNode(uuid,false);
};
window.attributeUpdate['_position']=function(uuid, name, raw, val){
		var oldValue = -1;
		if(node.attributes._position && node.attributes._position.newvalue)
		  oldValue = node.attributes._position;
        if(oldValue == -1 != val == -1){
          //structural reload;
		  onChangeNode(uuid, false);
        }else{
		  node.attributes._position = {'newvalue':val, 'value':raw};
		  attributeUpdate['_default'](uuid,"_position",id['value'],id['newvalue']);
		}
};
window.attributeUpdate['_name']=function(uuid, name, raw, val){
		var node = getNodeByUUID(uuid);
		node.attributes._name = {'newvalue':val, 'value':raw};
		var id = getNameOrId(node);
		attributeUpdate['_default'](uuid,"_nameOrId",id['value'],id['newvalue']);	
};
window.attributeUpdate['_default']=function(uuid, name, raw, val){
	
		var node = getNodeByUUID(uuid);
		if(raw==null && val == null)
			node.attributes[name] = null;
		else
			node.attributes[name]={'newvalue':val,'value':raw};
		var elem = document.getElementById(uuid+'-attribute-'+name);
		//TODO raw
		if(elem!=null && elem!==undefined){
			elem.innerHTML=(val!=null?val:"");
		}else{
			var elems = document.getElementsByClassName(uuid+'-attribute-'+name)
			Array.prototype.filter.call(elems, function(elem){
    			if(elem!=null && elem!==undefined){
					elem.innerHTML=(val!=null?val:"");
				}
			});
		}
		elem = document.getElementById(uuid+'-attribute-'+name+"-_raw");
		if(elem!=null && elem!==undefined){
			elem.innerHTML=(raw!=null?raw:"");
		}else{
			var elems = document.getElementsByClassName(uuid+'-attribute-'+name+"-_raw")
			Array.prototype.filter.call(elems, function(elem){
    			if(elem!=null && elem!==undefined){
					elem.innerHTML=(raw!=null?raw:"");
				}
			});
		}
	};
window.attributeUpdate['_description']=function(uuid,name,raw,val){
		attributeUpdate['_default'](uuid,"_description",raw,val);
		var des = getDescription(uuid);
		attributeUpdate['_vdescription'](uuid,"_vdescription",des['value'],des['newvalue']);
	};
window.attributeUpdate['_textNodeType']=function(uuid,name,raw,val){
		var node = getNodeByUUID(uuid);
		attributeUpdate['_default'](uuid,name,raw,val);
		//changeStatus(uuid,node.attributes._type.value,val);
		//TODO more clever update
		onChangeNode(uuid,false);
	};
window.attributeUpdate['_locations']=
	function(uuid, name, raw, val){
		attributeUpdate['_default'](uuid,"_locations",raw,val);
		var nodeData = getNodeByUUID(uuid);
		formLocationDesc(nodeData);
	};
window.attributeUpdate['_vdescription']=function(uuid, name,raw,val){
		var nodeData = getNodeByUUID(uuid);
		var addDesc = false;
		var nodes = $('.'+uuid+'-attribute-_vdescription');
		attributeUpdate['_default'](uuid,"_vdescription",raw.replace(/\\\"/g,"\""),val.replace(/\\\"/g,"\""));
		nodes.each(function() {
			var node = $(this); 
			var hd = node.hasClass("hiddenPoint");
			//printToConsole("hasH "+hd?"t":"f");
			if(nodeData.attributes._vdescription.value.replace("&nbsp;","").trim().length>0){
				if(hd)
					node.removeClass("hiddenPoint");
			}else{
				if(!hd)
					node.addClass("hiddenPoint");
			}
		});
		
	};

if(typeof constructNodeProcessor === "undefined")
	constructNodeProcessor = [];
// List of HTML block constructors for different types of nodes
constructNodeProcessor['TextNode']= function (nodeData, uuid) {
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
		//if (!nodeData.attributes._description || !nodeData.attributes._description.value) {
			// Description is empty, request all the locations assigned to this requirement
			// and construct description by combining them together
		//}
		formLocationDesc(nodeData);
		var des = getDescription(uuid);
		nodeData.attributes._vdescription = {'value':des['value'],'newvalue':des['newvalue']};
		// Fill in default values for missing attributes
			

		if (!nodeData.attributes._textNodeType || !nodeData.attributes._textNodeType.value)
			nodeData.attributes._textNodeType = { 'value': 'text' };
		// Construct HTML block
		var hname = "div";
		
		nodeData.attributes._nameOrId=getNameOrId(nodeData);
		
		var rawvalue = nodeData.attributes._vdescription.value;
		var value = nodeData.attributes._vdescription.newvalue;
		
		var isHeader = nodeData.isHeader;
		var addS = '';
		var elid=' id="'+uuid+'-attribute-_vdescription"';
		var relid=' id="'+uuid+'-attribute-_vdescription-_raw"';
		var clss='tndesc';
		
		if(isHeader){
			addS = "font-size:"+calcSize(nodeData.getDeep())+"%;font-weight:bold;";
			hname = 'div';
			value = nodeData.attributes._nameOrId.newvalue;
			rawvalue = nodeData.attributes._nameOrId.value;
			elid=' id="'+uuid+'-attribute-_nameOrId"';
			relid=' id="'+uuid+'-attribute-_nameOrId-_raw"';
			clss='tnheader';
		}
		
		var newNode= $(
			'<div class="indent nodewrapper" id="innerblock-' + uuid + '">' +			
			  '<div class="textnode supported-node dropTarget" id="node-' + uuid + '">' +
				'<div>'+
				  '<table cellpadding="0" cellspacing="0" border="0" width="100%">' +
			      '<tr>' +
			      	(extraAttrsString.length==0?"":
				      	('<td class="extraAttrsC">'+
				      		'<span class="extraAttrs" id="extraAttrs-'+uuid+'">'+
					      		extraAttrsString+
					      	'</span>'+
				      	'</td>')) +
		            '<td valign="top"">' +
		            '<img src="images/' + getNodeStatus('TextNode',nodeData.attributes._textNodeType.value).icon + '" alt="' + nodeData.attributes._textNodeType.value + '" title="' + nodeData.attributes._textNodeType.value + '" class="imgstatus" id="status-' + uuid + '" class="imgstatus" onclick="javascript:execNodeCommand(\'TextNode\', \'' + uuid + '\', \'chstatus\');" />' +
				    '</td>' +
					(!isHeader?"":
			      		'<td>'+
			      			'<'+hname+' style="margin:0px;padding:0px;">'+
			      				'<span style="'+addS+'" id="'+uuid+'-attribute-_numericId">'+nodeData.getPath()+'</span>'+
				      		'</'+hname+">" +
			      		'</td>'
			      	)+
			        '<td width="100%">'+
			        	'<'+hname+' style="margin:0px;padding:0px;min-width:100%-;" class="tndesc dragable" id="tndesc-' + uuid + '">'+
			        	'<div class="'+clss+'-' + uuid + '-_raw hiddenPoint" '+relid+'>'+ rawvalue + '</div>' +
			        	'<div class="'+clss+'-' + uuid + ' tndesc nodedesc '+editable_prefix+editable_prefix2+'reditable" contenteditable="false" style="'+addS+'"'+elid+'>'+ value + '</div>' +
					     '</'+hname+'>'+ 
				    '</td>' +
			        '<td valign="top">' +  
			        '<div class="textnodectrls nodectrls" style="float: right top;" bgcolor = red>' +
				      '<div style="text-align: right;">' +
				        '<img src="images/menu.png" alt="[v]" />' +
				      '</div>' +
				      '<div class="menu" id="menu-'+uuid+'"  style="display: none;">' +
				        constructMenu('TextNode', uuid) +
				      '</div>' +
				    '</div>' +
			        '</td>' +
			      '</tr>' +
			    '</table>' +
			  '</div>' +
			  '</div>'+
			  '<div id="addtext-' + uuid + '" width="100%" class = "focusedline createText" contenteditable="false"/>'+
			'</div>' 
			
		);
		
		var dchild = $("<span/>");
		dchild.attr("class","dummychildren");
		newNode.append(dchild);

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
		// Description is empty, request all the locations assigned to this requirement
		// and construct description by combining them together
		formLocationDesc(nodeData);
		var des = getDescription(uuid);
		
		nodeData.attributes._vdescription = {'value':des['value'],'newvalue':des['newvalue']};
		// Fill in default values for missing attributes
		nodeData.attributes._nameOrId = getNameOrId(nodeData);
		
		if (!nodeData.attributes.coverageStatus || !nodeData.attributes.coverageStatus.value)
			nodeData.attributes.coverageStatus = { 'value': 'in process' };
		var hname = null;
		var addDesc = false;
		var addS = '';
		nodeData.setDeep = function(deep){
			if(deep===NaN)
				deep = 0;
			nodeData.deep = deep;
			$('#reqdesc-'+nodeData.uuid).css({ 'font-size': calcSize(deep)+'%;' });
		}
		if(isNodeHeader(uuid)){
			addS = "font-size:"+calcSize(nodeData.getDeep())+"%;"+"font-weight:bold;";
			hname = 'div';
			value = nodeData.attributes._nameOrId;
			if(nodeData.attributes._vdescription.newvalue.replace("&nbsp;","").trim().length>0)
				addDesc = true;
		}else
			addDesc = true;
		
		// Create the block for requirement
		var editable_prefix = (((readOnlyMode & EDIT_MODE_REQ_RO) == 0) ? '' : 'non');
		// Remove "Delete requirement" where we cannot delete node
		var candel = canDeleteNode(uuid);
		var editable_prefix2 = '';
        if (!candel && !editable_prefix)
            editable_prefix2 = 'non';
        var desctext = 
        	'<div id="reqdesc-'+uuid+'-_raw" class="hiddenPoint '+uuid+'-attribute-_vdescription-_raw">'+
        	nodeData.attributes._vdescription.value +
        	'</div>'+
        	'<div id="reqdesc-'+uuid+'" class="'+uuid+'-attribute-_vdescription reqdesc dragable'+(addDesc?"":" hiddenPoint")+' nodedesc ' + editable_prefix + 'reditable" contenteditable="false" >' +
			nodeData.attributes._vdescription.newvalue +
	  		'</div>';
	  	var addTextLine = '<div id="addtext-' + uuid + '" width="100%" class = "focusedline createText" contenteditable="false"/>';
	  	if(getType(getParentUUID(uuid))!="Requirement")
	  		addTextLine = "";
        var newNode = $(
			'<div class="indent nodewrapper" id="innerblock-' + uuid + '">' +

			  '<div class="requirement supported-node dropTarget" id="node-' + uuid + '">' +
			  	(extraAttrsString.length==0?"":
				      	('<span class="extraAttrsC">'+
				      		'<span class="extraAttrs" id="extraAttrs-'+uuid+'">'+
				      			extraAttrsString+
				      		'</span>'+
				      	'</span>')) +
				'<div  style="overflow: hiddenPoint;">'+
			    '<table cellpadding="0" cellspacing="0" border="0" width="100%" class="reqname_wrap">' +
			      '<tr>' +
			      	(hname==null?"":
			      		'<td>'+
			      			'<'+hname+' style="margin:0px;padding:0px;">'+
			      				'<span style="'+addS+'" id="'+uuid+'-attribute-_numericId">'+
									nodeData.getPath()+
								'</span>'+
				      		'</'+hname+">"+
			      		'</td>'
			      	)+
			      	'<td width="100%">'+
					  (hname!=null?
					  '<div class="hiddenPoint" id="'+uuid+'-attribute-_nameOrId-_raw">' +nodeData.attributes._nameOrId.value +'</div>' +
		            	'<'+hname+' style="margin:0px;padding:0px;min-width:100%-;" class="dragable reqname" id = "reqname-' + uuid + '">'+
			          		'<div class="reqname-' + uuid + ' reqname nodetitle ' +editable_prefix+ editable_prefix2 + 'renamable" contenteditable="false" id="'+uuid+'-attribute-_nameOrId" style="'+addS+'" >' +nodeData.attributes._nameOrId.newvalue +'</div>' +
			          	'</'+hname+">"
			          	:desctext
					  	) +
			        '</td>' +
			        '<td valign=top align=right>' +
			          '<div class="reqctrls nodectrls">' +
			            '<div style="text-align: right;">' +
			              '<img src="images/menu.png" alt="[v]" />' +
			            '</div>' +
			            '<div class="menu" id="menu-'+uuid+'"  style="display: none;">' +
			              constructMenu('Requirement', uuid) +
			            '</div>' +
			          '</div>' +
			        '</td>' +
			      '</tr>' +
			    '</table>' +
			    (isNodeHeader(uuid)?desctext:"")+
			    '</div>'+
			  '</div>' +
			  addTextLine +
			'</div>' 
		);
		
		// Enumerate all children objects and recursively construct the rest of the tree
		var dchild = $("<span/>");
		dchild.attr("class","dummychildren");
		newNode.append(dchild);
        // Remove "Add sibling requirement" command from root node
		//var rootNode = getStartNode();
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
		//newNode.append("</>")
        // Remove "Add sibling requirement" command from root node
		return newNode;
	};
constructNodeChildren['Requirement'] = constructNodeChildren['TextNode'] = function (nodeData) {
	var n = constructNodeChildren['_default'](nodeData);
	for (var i = 0; i < nodeData.children.length; ++i) {
		var chiuid = nodeData.children[i];
		loadNodeThen(chiuid, function(){
			var chiNode = getNodeByUUID(chiuid);
			if(!chiNode)
				return;
			var chldType = chiNode.attributes._type.value;
			if (chldType == 'TestPurpose')
				$($("#block-"+nodeData.uuid).find('.menu-item-addreqchld')[0]).remove();
		});
	}
	return n;
};
