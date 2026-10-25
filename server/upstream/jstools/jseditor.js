if (!Array.prototype.includes) {
  Object.defineProperty(Array.prototype, "includes", {
    enumerable: false,
    value: function(obj) {
        var newArr = this.filter(function(el) {
          return el == obj;
        });
        return newArr.length > 0;
      }
  });
}
if (!(function f() {}).name) {
    Object.defineProperty(Function.prototype, 'name', {
        get: function() {
            var name = (this.toString().match(/^function\s*([^\s(]+)/) || [])[1];
            // For better performance only parse once, and then cache the
            // result through a new accessor for repeated access.
            Object.defineProperty(this, 'name', { value: name });
            return name;
        }
    });
}
var hotkeymode = {
		"handlerCKEditor":function(){return countEditorInstances()>0?true:false;}
};

//disable standard inline editor model
if(!window.attributeUpdate)
  window.attributeUpdate = [];
window.attributeUpdate["_locations"]=function(uuid,name,raw,val){
	
};
window.attributeUpdate["_numericId"]=function(uuid,name,raw,val){
	var node = getNodeByUUID(uuid);
	node.setPath(raw);
	node.setDeep(node.getParentDeep()+1);
	var deff = window.attributeUpdate['_default'];
	deff(uuid, name, raw, val);
};
templateLoadedActions = function(){};//needs to be inited by editor
window.templateLabel = [];
var draggedNode = null;
var isUpdating = false;
var cache = {};
var allowedTypes=[];
var roots = [];
var extraAttrs=extraAttrsNames;
var MENU_OFFSET = 50;
var urlConsts = {
	"25":"%",
	"21":"!",
	"2A":"*",
	"22":"\"",
	"27":"'",
	"28":"(",
	"20":" ",
	"29":")",
	"3B":";",
	"3A":":",
	"40":"@",
	"26":"&",
	"3D":"=",
	"2B":"+",
	"24":"$",
	"2C":",",
	"3F":"?",
	"23":"#",
	"5B":"[",
	"5D":"]"
}
if(!("TP_ALLOWED" in window))
	TP_ALLOWED = false;
if(!("COMMENT_ALLOWED" in window))
	COMMENT_ALLOWED = false;
constructNodeChildren["_default"]= function(nodeData){
		var newNode = document.createElement("div");
		newNode.id="allchildren-"+nodeData.uuid;
		newNode.className="collapsable";
		var nnewNode = $(newNode);
		var childrenNodes = [];
		var j = -1;
		for (var i = 0;i<nodeData.children.length;i++) {
			if(isNodeHeader(nodeData.children[i]))
				j++;
			// Construct the next child subtree (for requirements) or table (for test purposes)
			var chldNode = constructDummyTree(nodeData.children[i]);
			var chldType = chldNode.type;
			if ( synonim[chldType] )
				chldType = synonim[chldType];
			if (!childrenNodes[chldType]) {
				var wrapperConstructor = constructNodeWrapperProcessor[chldType];
				if (wrapperConstructor)
					childrenNodes[chldType] = wrapperConstructor(nodeData.uuid);
				else
					childrenNodes[chldType] = $('<div />');
			}
			childrenNodes[chldType].append(chldNode);
		}
		var hasChildren=hasVisibleChildren(nodeData.uuid);
		// Now append children nodes blocks to the current requirement node
		for (var i = 0; i < childrenNodesOrder.length; ++i) {
			var chldType = childrenNodesOrder[i];
			if (childrenNodes[chldType]) {
				nnewNode.append(childrenNodes[chldType]);
				if(chldType == "General" && hasChildren){
                    nnewNode.addClass('collapsable');
                    nnewNode.prepend($('<img src="images/collapse.png" alt="[&minus;]" class="plusminus" onclick="javascript:toggleChildren(\'' + nodeData.uuid + '\');" />'));
				}
			}
		}
		return nnewNode;
	};
	///socket calls
	//METHODS
	
function printToConsole(msg)
{
	waitSend(JSON.stringify({
			"operation": "printToConsole",
			"message":msg==null?"null":msg
		}));
}
function sendUploadImage(url){
	waitSend(JSON.stringify({
			"operation": "uploadImage",
			"path":url==null?"null":url
		}));
}

function callOpenTemplates(node)
{
	waitSend("{\"operation\":\"callOpenTemplates\",\"node\":\""+(!node?"null":node)+"\"}");
}
function changeNodeId(node, newId)
{
	waitSend("{\"operation\":\"changeNodeId\",\"node\":\""
	+(node==null?"null":node)
	+"\", \"newId\":\""+(newId==null?"null":newId)+"}");
}
function loadTemplates(){
	waitSend("{\"operation\":\"loadTemplates\"}");
}

function sendInited(){
	waitSend("{\"operation\":\"sendInited\"}");
}


function callEditNode(node){
	waitSend("{\"operation\":\"callEditNode\",\"inTool\":\""+inTool+"\",\"node\":\""+(node==null?"null":node)+"\"}");
}	
function createNode(uuid, type, title, addid, attributes){
	if(uuid == null)
		uuid = "null";
	if(type == null)
		type = "null";
	if(typeof title === "undefined")
		title = "";
	if(typeof addid === "undefined")
		addid = "";
	if(typeof attributes === "undefined")
		attributes = [];
	waitSend(
		JSON.stringify({
			"operation": "createNode",
			"node":uuid,
			"type":type,
			"title": title,
			"addid": addid,
			"inTool": inTool,
			"attributes":attributes
		}));
}

function addTest(uuid){
	waitSend("{\"operation\":\"addTest\",\"inTool\":\""+inTool+"\",\"node\":\""+(uuid==null?"null":uuid)+"\"}");
}
function openIdChange(node, title, message, lastValue){
	if(typeof lastValue === "undefined")
		lastValue = "";
	waitSend(
		JSON.stringify({
			"operation": "openIdChange",
			"node":node,
			"title": title,
			"message": message,
			"lastValue":lastValue
		}));
}
function changeNodeName(node, newName){
	waitSend(
		JSON.stringify({
			"operation": "changeNodeName",
			"node":node,
			"newName": newName
		}));
}
function checkName(node, newName, parent){
	if(typeof parent === "undefined")
		parent = "";
	waitSend(
		JSON.stringify({
			"operation": "changeName",
			"node":node,
			"newName": newName,
			"parent": parent
		}));
}
function changeNodeAttr(node, key, value){
	if(typeof value === "undefined")
		value = "";
	waitSend(
		JSON.stringify({
			"operation": "changeNodeAttr",
			"node":node,
			"key": key,
			"value": value
		}));
}
 
function changeNodeParent(node, newParent, altPressed){
	if(typeof altPressed === "undefined")
		altPressed = false;
	waitSend(
		JSON.stringify({
			"operation": "changeNodeParent",
			"node":node,
			"newParent": newParent,
			"shiftPressed": altPressed
		}));
}
function removeNode(node){
	waitSend(
		JSON.stringify({
			"operation": "removeNode",
			"node":node
		}));
}
function setRequalityFocus(node){
	waitSend(
		JSON.stringify({
			"operation": "setRequalityFocus",
			"node":node
		}));
}
function copyNode(node, cut){
	if(typeof cut === "undefined")
		cut = false;
	waitSend(
		JSON.stringify({
			"operation": "copyNode",
			"node":node, 
			"cut": cut
		}));
}
function pasteNode(node, pasteTree, nodeAfter){
	if(typeof pasteTree === "undefined")
		pasteTree = false;
	if(typeof nodeAfter === "undefined")
		nodeAfter = "";
	waitSend(
		JSON.stringify({
			"operation": "pasteNode",
			"inTool":inTool,
			"node": node,
			"pasteTree": pasteTree,
			"nodeAfter": nodeAfter
		}));
}
function fireRedLinkSelection(node, link){
	
	waitSend(
		JSON.stringify({
			"operation": "fireRedLinkSelection",
			"node": node,
			"link": link
		}));
}
function createTestStep(node, nodeAfter){
	if(typeof nodeAfter === "undefined")
		nodeAfter = "";
	waitSend(
		JSON.stringify({
			"operation": "createTestStep",
			"node": node,
			"nodeAfter": nodeAfter
		}));
}
function removeTestPurposeLink(node){
	waitSend(
		JSON.stringify({
			"operation": "removeTestPurposeLink",
			"node": node
		}));
}
function addSingleTestPurposeToTestStep(node){
	waitSend(
		JSON.stringify({
			"operation": "addSingleTestPurposeToTestStep",
			"node": node
		}));
}
function openEditorForNode(node){
	waitSend(
		JSON.stringify({
			"operation": "openEditorForNode",
			"inTool": inTool,
			"node": node
		}));
}
function loadNodeByUUID(nde){
	if(nde == null)
		return;
	waitSend(
		JSON.stringify({
			"operation": "loadNodeByUUID",
			"node": nde
		}));
}
function loadNodeTreeByUUID(node){
	waitSend(
		JSON.stringify({
			"operation": "loadNodeTreeByUUID",
			"node": node
		}));
}
function addRootNode(node){
	roots.push(node);
}

function checkChilds(node,newName,childs){
	waitSend(
		JSON.stringify({
			"operation": "checkChilds",
			"newName": newName,
			"childs": childs,
			"node": node
		}));
}
function addNewTestPurposeToEditor(euuid, sel, node, tolink){
	waitSend(
		JSON.stringify({
			"operation": "addNewTestPurposeToEditor",
			"euuid" : euuid,
			"sel" : sel,
			"node": node,
			"tolink" : tolink
		}));
}

function copyPasteNode(node, pasteNode, cut, pasteSib){
  copyNode(node, cut);
  if(pasteSib)
    pasteNodeAsSibling(pasteNode);
  else
    pasteNodeAsChild(pasteNode);
  
}

//node, newParent, altPressed
function getActiveTemplateLabel(uid, type){
	return window.templateLabel[type];
}
function decodeEncodedChars(string){
	if(!string || !string.indexOf)
		return string;
	var cnt = 0;
	var ind = string.indexOf("%");
	
	while(ind>-1){
		var c = urlConsts[string.substr(ind+1,2).toUpperCase()];
		var end = ind+3;
		if(c)
		{}else{
		  var oind = ind;
          ind = string.indexOf("%",ind+1);
          var prev = oind;
          while(ind == prev+3){
	        prev = ind;
            ind = string.indexOf("%",ind+1);
          }
		  end = prev+3;
		  try{
		    c = decodeURIComponent(string.substr(oind,prev-oind+3));
          }catch(e){}//in most cases %+smth

		  ind = oind;
		}
		if(c){
			string = string.substr(0,ind)+c+string.substr(end);
			ind = string.indexOf("%",ind+c.length);
		}else
			ind = string.indexOf("%",ind+1);
	}
	return string;
}
function updatePath(chi, forceUpdate){
	var node = cache[chi];
	if(!node)
		return;
	var dom = document.getElementById(node.uuid+"-attribute-_numericId");
	var domPath = "";
	if(dom)
		domPath = dom.innerHTML;
	var updateP = false;
	var par = typeof(node.parent)!=="undefined"?node.parent:null;
	var parentDeep = -1;
	var updateAttr = false;
	var nodePath = domPath.length>0?domPath:node.hasPath()?node.getPath():"";
	var nodeHasPath = nodePath.length>0;
	if(par){
		var parNode = cache[par];
		parPath = parNode && typeof(parNode.getPath())!=="undefined"?parNode.getPath():"";
		if(parNode){
			if((!nodeHasPath || !nodePath.startsWith(parPath)))
			{
				updateP = true;
				parentDeep = parNode.deep;
				replPath = parPath;
				if(parNode.hasPath())
					replPath = replPath+".";
				replPath = replPath+getChildNumber(chi);
			}
		}else if(!nodeHasPath)
			updateAttr = true;
	}else 
		updateAttr = true;
	if(updateAttr)
	{
		if(!node || typeof node.attributes === "undefined")
			return;
		var attr = node.attributes["_numericId"];
		if(attr && (!nodeHasPath || nodePath!=(attr.newvalue))){
			updateP = true;
			replPath = attr.newvalue;
		}
	}
	if(forceUpdate && !updateP){
		var attr = node.attributes["_numericId"];
		if(attr){
			updateP = true;
			replPath = attr.newvalue;
			parentDeep = node.deep-1;
		}else{
			if(node.hasPath()){
				updateP = true;
				replPath = node.getPath();
				parentDeep = node.deep-1;
			}else
				replPath = node.getPath();
		}
	}
	if(updateP || forceUpdate){
		node.setPath(replPath);
		node.setDeep(parentDeep+1);
		if(typeof(node.children)!=="undefined" && node.children)
			for(var i = 0;i<node.children.length;i++)
				updatePath(node.children[i], forceUpdate);
	}
}
function addToCache(node){
	
	if(Array.isArray(node)){
		var added = [];
		for (var i=0;i<node.length;i++)
			if(addToCache(node[i]))
				added.push(node[i]);
		for (var i=0;i<node.length;i++){
			updatePath(node[i].uuid, false);
		}
	}else{
		try{
			if(!(node.uuid in cache)){
				cache[node.uuid]=node;
				if(node.children)
					for(var i = 0;i<node.children.length;i++)
						parentMap[node.children[i]]=node.uuid;
				var par = parentMap[node.uuid];
				for(s in node.attributes)
				{
					if(node.attributes[s].value)
						node.attributes[s].value = decodeEncodedChars(node.attributes[s].value)
					node.attributes[s].newvalue = decodeEncodedChars(node.attributes[s].newvalue) 
				}
				node.path="";
				node.deep = 0;
				node.hasPath = function(){
					return node.attributes["_numericId"]!=null;
				}
				node.getPath = function(){
					if(node.attributes["_numericId"]!=null)
						return node.attributes["_numericId"].newvalue; 
					return null;
				}
				node.setPath = function(path){
					node.path = path;
					var dom = document.getElementById(node.uuid+"-attribute-_numericId");
					if(dom)
						dom.innerHTML = path;
				}
				node.getDeep = function(){
					if((typeof (node.deep)) !== "undefined" && node.deep!==NaN)
						return node.deep;
					var par = parentMap[node.uuid];
					if(par)
						node.deep = getNodeByUUID(par).getDeep()+1;
					else
						node.deep = 0;
				};
				node.setDeep = function (deep){
					node.deep = deep;
				};
				node.getParentDeep = function(){
					if(typeof node.parent !== "undefined" && node.parent){
						var parNode = getNodeByUUID(node.parent);
						if(parNode)
							return parNode.getDeep();
					}
					return -1;
				}
				if(par)
					node.parent = par;
				
				return true;
			}
		}catch(e){
			alert(e+" "+e.stack);
		}
		//if(node.children)
			//for (var i=0;i<node.children.length;i++) 
				//addToCache(node.children[i]);
	}
	return false;
}

function loadNodeSubtreeByUUID(uuid){
	if(!(uuid in cache)){
		var node = loadNodeTreeByUUID(uuid);
		if(node)
			addToCache($.parseJSON(node));
	}
	return cache[uuid];
}
var parentMap = [];
function getCachedNodeByUUID(uuid){
	if(!(uuid in cache)){
		return null;
	}
	return cache[uuid];
}
var reactOnLoad = [];
var reactOnLoadTree = [];
function loadNodeThen(uuid, func){
	if(uuid in cache){
		func();
		return;
	}
	if(!(uuid in reactOnLoad))
		reactOnLoad[uuid] = [];
	reactOnLoad[uuid].push(func);
	loadNodeByUUID(uuid);
}

function getNodeByUUID(uuid){
	//if(!(uuid in cache)){
		//var node = loadNodeByUUID(uuid);
		
		//if(node)
			//addToCache($.parseJSON(node));
//	}
	return cache[uuid];
}
function getHotKeyMode(){
	for (var key in hotkeymode) {
		  if ({}.hasOwnProperty.apply(hotkeymode, [key])) {
			  if(hotkeymode[key] && hotkeymode[key]())
			  	return key;
		  }
	}
	return 'handlerGlobal';
}
var isSuitable = {
	'addChildRequirementConfirm':function(uuid){
		var type = getType(uuid);
		if(type=="Requirement" || type =="TextNode")
		{
			return Number(countChildrenOfType(uuid, "TestPurpose"))==0;
		}
		return false;
	},
	'addSiblingRequirementConfirm':function(uuid){
		var par = getParentUUID(uuid);
		return uuid!=startNode&&Number(countChildrenOfType(par, "TestPurpose"))==0 && getType(par) == "Requirement";
	},
	'addSiblingTextNodeConfirm':function(uuid){
		var par = getParentUUID(uuid);
		var tp = getType(par);
		if (Number(countChildrenOfType(par, "TestPurpose"))>0)
			return false;
		return uuid!=startNode;
	},
	'callAddTest':function(uuid){
        var type = getType(uuid);
		return inTool&&(type=="Requirement"||type=="TextNode"||type=="TestPurpose");
	},
	'addChildTextNodeConfirm':function(uuid){
		if (Number(countChildrenOfType(uuid, "TestPurpose"))>0)
			return false;
		return true;
	},
	'editNode':function(uuid){return inTool&&isEditable($("#block-"+uuid))},
	'switchRequirementType':function(uuid){
		var type = getType(uuid);
		if(type=="Requirement")
		{
			return Number(countChildrenOfType(uuid, "Requirement"))==0&&Number(countChildrenOfType(uuid, "TestPurpose"))==0;
		}			
		return type=="TextNode" && getType(getParentUUID(uuid))!="TextNode" && Number(countChildrenOfType(getParentUUID(uuid), "TestPurpose"))==0 && Number(countChildrenOfType(uuid, "Requirement"))==0;
	},
	'addTestPurpose':function(uuid){
		if(!TP_ALLOWED)
			return false;
		var type = getType(uuid);
		if(type=="Requirement")
		{
			return Number(countChildrenOfType(uuid, "Requirement"))==0&&Number(countChildrenOfType(uuid, "TextNode"))==0; 
		}			
		return getType(uuid)=='TestPurpose';
	},
	'addComment':function(uuid){
		if(!COMMENT_ALLOWED)
			return false;
		return true;//getType(uuid)=='Comment'; 
	},
	'openEditorForNode':function(uuid){return inTool;},
	'openTemplates':function(uuid){return inTool;},
	'deleteNode':function(uuid){return canDeleteNode(uuid);}
};


//Time (in milliseconds) that animations will take
var animationPeriod = 200;

//jQuery array of all elements that can accept keyboard focus
var all_active_elems;

//jQuery array of all elements that can be opened for editing
var all_reditable_elems;

//Index of the element that has keyboard focus
var keyboard_focus;

//Concept: no more than one editor instance is opened at any moment of time.
//Keep this instance in a global variable.
var editor = null;
var editorTempContent = null;
var activeEditorElem = null;
var editorOpening = false;
var editorJavaDialog = false;
var closeStarted = false;


//Keeps the current read-only mode (ORed values)

//Created node to change focus on
var nodeToFocus = null;

var cachedDropTargets = null;

const EDIT_MODE_REQ_RO = 1;  // Requirements are read-only
const EDIT_MODE_TN_RO = 16; //text nodes are read only
const EDIT_MODE_VIRTUAL = 8;  // Requirements are read-only

//Regular expression to find [linkto: ]
var linktoexp = new RegExp(
		'\\[<span id="([a-zA-Z0-9-]+)">.{0,10}</span>linkto: ([^\\]]*)\\]',
		"mg");
var newlinktoexp = new RegExp(
     '\\[(?:<span id="([a-zA-Z0-9-]+)">.{0,10}</span>)?linkto: ([^\\]]*)\\]',
     "mg");
function openGlobalMenu(sel){
	loadNodeThen(uuid, function(){
		//TODO open node menu on top
	});
}
//function to get displayed version of link in
//regular expressions. 
//Arguments: 
//str - full expression to replace([linkto:...])
//arg1 - if link is new(entered by user),
//contains QualifiedID of target, otherwise, contains
//UUID of target
//arg2 - if linkto contains information about UUID
//(not new one), contains QualifiedID of target
function getLinkForm(str, arg1, arg2) {
	uuid = arg1;
	ufname = arg2;
	//if node was deleted
	newufname = getUFName(uuid);
 var t = document.createElement( 'div' );
 t.innerHTML = newufname;
 newufname = t.innerHTML;
 if((newufname == null)
 //or new Ufname!=old on
 ||(ufname != newufname))
     return "[<span id=\"" + uuid + "\">&zwnj;</span>linkto: " + ufname + "]";
	return '<span class = "linkspan clickable" id = "linkspan-' + uuid + '" onclick = "javascript:openEditorForNode(\'' + ufname + '\');">'
		+ ufname + '</span>';
}
function switchRequirementType(uuid){
	var newType = 'Requirement';
	const fuuid = uuid;
	loadNodeThen(uuid, function(){
		if(!isSuitable['switchRequirementType'](fuuid))
			return;
		if(getType(fuuid)=='Requirement')
			newType = 'TextNode';
		else
			if(getType(fuuid)!='TextNode')
				return;
		changeNodeAttr(fuuid, '_type', newType);
	});
}
function getParentNodeWithClass(element, cls) {
 if (element.className != null && (' ' + element.className + ' ').indexOf(' ' + cls + ' ') > -1)
     return element;
 if(element.parentNode != null)
     return getParentNodeWithClass(element.parentNode,cls);
 return null;
}

function updateLinkDataToDisplay(s){
 s = s.replace(linktoexp, getLinkForm);
 return s.replace(newlinktoexp, markWrongLinks);
}

function getDataOfLinkTo(uuid, linkto){
	if(uuid == null || uuid == "")
		return '[linkto: ' + linkto.replace(/  /g,"&nbsp;&nbsp;") + "]";
	return '[<span id="' + uuid
        + '">&zwnj;</span>linkto: ' + linkto.replace(/  /g,"&nbsp;&nbsp;") + "]";
}

function getNodeStatus(type, val){
	if(type in node_statuses){
		if(val in node_statuses[type]){
			return node_statuses[type][val];
		}
	}
	return node_statuses['Requirement']['in process'];
	
}

function formLinkTo(muuid, m){
return '<span class = "clickable linkspan" id = "linkspan-' + muuid + '"><a href="javascript:openEditorForNode(\'' + m + '\');">' 
+ m + '</a></span>';
}
function wrongLinkUpdate(euuid, sel, node, ufname){
    tsnode = $('#tsdesc-' + euuid)[0];
    var r = sel.ownerDocument.createRange();
    r.setStartBefore(sel);
    var df = r.createContextualFragment(formLinkTo(node, ufname));
    sel.parentNode.replaceChild(df, sel);
    replaceRefAttributeValue(euuid, "_links", node);
    //changeNodeAttr(euuid, '_description', tsnode.innerHTML);
}
function fireRedLinkSelection(sel){
	if(sel == null)
		return;
	euuid = getParentNodeWithClass(sel, "nodewrapper");
    if(euuid == null)
        return null;
    euuid = extractUUID(euuid.id);
    uuid = sel.id;
    fireRedLinkSelection(euuid, uuid);
    
    
    //TODO all code below move to reaction
    tolink = uuid.substring(uuid.lastIndexOf("-") + 1);
    //get node that includes this link
    //get type filter to linkto
    nodes = getNodesByUFName(tolink, euuid.substring(euuid.indexOf("_")));
	uuid = uuid.substring(0, uuid.lastIndexOf("-"));
	newlink = null;
    //get test step
    if(uuid != "")
		newlink = getUFName(uuid);
	if(uuid != "" && newlink != null)
	{
		if(newlink != tolink){
			if(nodes == null){
				nodes = [uuid];
			}else{
			    nodes[nodes.length] = uuid;
			}
		    if(nodes.length >= 1){
		    	//select from list
		    	node = fireNodeSelectionFromList(nodes, "Element was renamed. Select appropriate node from list.");
		    	if(node != null){
		    		wrongLinkUpdate(euuid, sel, node, getUFName(node));
		    	}
		    }else{
		    	//we not select from list or
	            addNewTestPurposeToEditor(euuid, sel);
		    }
		}else{
			//uuid is proper, name is proper - all good
			wrongLinkUpdate(euuid, sel, uuid, tolink);
		}
	}else{
		if(nodes == null){
            nodes = [];
        }
        if(nodes.length >= 1){
            //select from list
            node = fireNodeSelectionFromList(nodes, "Element was deleted. Select appropriate node from list.");
            if(node != null){
            	wrongLinkUpdate(euuid, sel, node, getUFName(node));
            }
        }else{
            //we not select from list
            addNewTestPurposeToEditor(euuid, sel);//needs to fire wrongLinkUpdate
        }
	}
	return null;
}

function fromViewToData(uuid){
	var elems = $('#block-' + uuid).find(".linkspan");
    //get all links in edited element
    for ( var i = 0; i < elems.length; i++) {
        //get node by class
        //euuid = getParentNodeWithClass(elems[i],"nodewrapper");
        //if(euuid != null)
        //    euuid = extractUUID(euuid.id);
        //if(euuid != uuid)
        //    continue;
        ruuid = extractUUID(elems[i].id);
        ufname = getUFName(ruuid);
        if(ufname != null){
            var r = elems[i].ownerDocument.createRange();
            r.setStartBefore(elems[i]);
            var df = r.createContextualFragment('[<span id="' + ruuid
                    + '">&zwnj;</span>linkto: '
                    + ufname + ']');
            elems[i].parentNode.replaceChild(df, elems[i]);
        }
    }
    var elems = $('#block-' + uuid).find(".wronglink");
    //get all links in edited element
    for ( var i = 0; i < elems.length; i++) {
        //euuid = getParentNodeWithClass(elems[i],"nodewrapper");
        //if(euuid != null)
        //    euuid = extractUUID(euuid.id);
        //if(euuid != uuid)
        //    continue;
        var r = elems[i].ownerDocument.createRange();
        r.setStartBefore(elems[i]);
        //get index from 'id'
        uid = extractUUID(elems[i].id);
        uid = uid.substring(0, uid.indexOf("-"));
        if(uid != "")
        	uid = '<span id="' + uid + '">&zwnj;</span>';
        var df = r.createContextualFragment('[' + uid + 'linkto: ' + elems[i].innerHTML + "]");
        elems[i].parentNode.replaceChild(df, elems[i]);
    }
}

function markWrongLinks(str, arg1,arg2){
    var link = arg2;
    var uuid = arg1;
    if(uuid == null)
    	uuid = "";
    if(link == "")
    	link = "empty";
	return s = '<span id="select-' + uuid + '-'+link.unescapeHtml()+'" style = "color:red" class="linkspan wronglink clickable" onclick="javascript:fireRedLinkSelection(this)">' + link + '</span>';
}


//HTML unescaping
String.prototype.unescapeHtml = function () {
	if(this == "")
		return "";
	var t = document.createElement( 'div' );
	t.innerHTML = this;
	return t.firstChild.nodeValue;
} 

//Aditional plugins for nodes. Now using to
//provide new button for linking. Contains pairs
//'edited_element' => 'plugin_name'. for ex.
//'reqdesc' => 'requality'.
//Can include several plugins, but in such format
//'field' => 'plugin_1, plugin_2, plugin_3'
//PS. requality includes by default, it doesn't
//need to be included. 
var nodePlugins = {};
//Actions to execute before editor will be open
//now used to perform string replacement for
//linking(change from displayed to stored format)
//Format : 'field' => function (elem.id){}
var beforeEditorOpenAction = {};
//Actions to execute when CKEditor Save command is called for different types of fields
var saveAction = {
	'reqname': function (uuid, new_data) {
		new_data = new_data.unescapeHtml();
		if(new_data == "")
			new_data = null;
		if(new_data != null)
			new_data = new_data.replace(/&nbsp;/g, " ");
		checkName(uuid,new_data);
	},
	'reqdesc': function (uuid, new_data) {
		changeNodeAttr(uuid, '_description', new_data);
	},
	'cmtdesc': function (uuid, new_data) {
		changeNodeAttr(uuid, '_description', new_data);
	},
	'tndesc': function (uuid, new_data) {
		changeNodeAttr(uuid, '_description', new_data);
	},
	'tnheader':function (uuid, new_data) {
		new_data = new_data.unescapeHtml();
		if(new_data == "")
			new_data = null;
		if(new_data != null)
			new_data = new_data.replace(/&nbsp;/g, " ");
		const fuuid = uuid;
		loadNodeThen(uuid, function(){
			checkName(fuuid,new_data);
		});
	},
	'addtext': function (uuid, new_data) {
		var par=getParentUUID(uuid);
		createNode(par,'TextNode',"Add Sibling Text Node[template]",uuid,[{'_description':new_data.replace(/&nbsp;/g, " ")}]);
		//TODO look on it
		/*var el = document.getElementById("addtext-"+uuid);
		el.innerHTML="";
		el.className="focusedline createText";
		el.style.cssText=null;)*/
	}
};
function getUFName(node){
	var nd = getNodeByUUID(node);
	if(node.attributes._name&&node.attributes._name.value)
		return node.attributes._name.value;//TODO mb newValue instead of value
	return getUFName(nd.parent) + node.attributes._id.value;
}
function canDeleteNode(node){
	//TODO test
	var nd = getNodeByUUID(node);
	if(!nd)
		return false;
	if(!nd.parent)
		return false;
	return true;
}
/**
* Search for aString with given options.
* <br><br><i>Can be used in java</i>
*/
function findText( aString
               , aCaseSensitive
               , aBackwards
               , aWrapAround
               )
{
  return window.find( aString
                    , aCaseSensitive
                    , aBackwards
                    , aWrapAround
                    , false
                    , false
                    , false
                    );
}
function selectFileDialog(exts){
	window.waitSend(JSON.stringify({
		'operation':"selectFileDialog",
		'exts':exts
	}));
}
function setSelectionText(val){
	 if(editor != null){
	 	editor.insertText(val.toString());
	 }
	    
}
function getSelectionText() {
	 	if(editor != null && editor.getSelection()!=null){
	 		return editor.getSelection().getSelectedText( );
	 	}
	    var selection = window.getSelection();
	    var text = "";
	    for( i = 0; i < selection.rangeCount; i++ )
	    {
	        text += selection.getRangeAt(i).toString();
	    }
	    return text;
	}

function getAttributeName(elem){
	var cl = elem.className+" ";
	var pos = cl.indexOf("attribute-");
	if(pos>-1){
		cl = cl.substr(pos+10,cl.indexOf(" ",pos+1)-pos-10)
		return cl;
	}
}

//Opens editor window in place of the specific DIV element. The previous editor
//instance (if present) is closed (saving all the changes).
//Input parameters:
//	elem       - DIV element to be replaced with the editor (if null, editor will not open)
//	simplified - whether editor should be a simplified version (for in-place renaming)
//cleanHtml  - whether editor should be a simplified version (for in-place renaming)
//Return value:
//	none
function putEditorWithHtmlClean(elem, simplified, cleanHtml) {
	if (!elem)
		return;
	if(isReadOnly(elem))
		return;
	if(activeEditorElem == elem.id)
		return;
	saveEditor();
	var relem = document.getElementById(elem.id+"-_raw");
	if(!editorTempContent)
		initTempContent();

	editorTempContent.innerHTML = elem.innerHTML;
	if(relem != null&&(relem.innerHTML!=null))
		elem.innerHTML=relem.innerHTML;
	editorOpening = true;
	// Clear the node-level clipboard
	internalClipboard = null;
	var editor, f, id, o_html, pk,odata;
    f = $(elem);
    f.attr("contenteditable", true);
    id = f.attr("id");
    pk = f.data("pk");
	var pos = elem.id.indexOf('-');
	activeEditorElem = elem.id;
	var classList = (f.attr('class')+" "+f.attr('id')).split(/\s+/);
	var add = '';
	for (var i = 0; i < classList.length; i++) {
		var id = classList[i];
		var pos = id.indexOf('-');
		var field_type = id.substr(0, pos);
		var uuid = id.substr(pos + 1);
		 //execude pre-opening actions
		 // for different fields
		if (beforeEditorOpenAction[field_type])
	      beforeEditorOpenAction[field_type](elem.id);
	    //get list of plugins for editor of this field
	    //for buttons+
	    if (nodePlugins[field_type] != null)
		 add += ',' + nodePlugins[field_type];
	 
	}
	 var field_type = elem.id.substr(0, pos);
	
	//$(elem).attr('contenteditable', 'true');
	var editor;
	conf = simplified ? miniConfig: normalConfig;
	
	conf["bodyId"] = 'editor'+elem.id;
	
	if(add.length>0 && !conf.extraPlugins.endsWith(add)){
		conf["extraPlugins"] += add;
	}
	
	editor = CKEDITOR.inline( elem.id , conf);
		
	editor.bodyId = conf.bodyId;
	editor.config.allowedContent = true;
	o_html = "";
	odata = "";
	editor.on("dataReady", function() {
      editor.odata = editor.getData();
      return editor.focus();
    });
	if(simplified)
	  CKEDITOR.on('instanceReady', menuHidder);
	else
	  CKEDITOR.removeListener('instanceReady', menuHidder);
    editor.on("focus", function() {
      return o_html = f[0].innerHTML;
    });
    editor.onClose = function(e){
       if(editorTempContent){
         elem.innerHTML=editorTempContent.innerHTML;
         editorTempContent.innerHTML="";
       }
       	if(editorJavaDialog)
			return false;
       try{
         activeEditorElem = null;
	     var n_html, view;
         f.attr("contenteditable", false);
         f.removeClass("cke_focus");
         f.css("position","");
         
	     editor.destroy(false);
	     CKEDITOR.remove(editor);
	     
       }catch(ea){
    	 alert(ea);
       }
    };
    editor.on("blur", function() {
        f.attr("contenteditable", false);
        n_html = f[0].innerHTML;
        if(editor && editor.status != "destroyed"){
		    if(o_html !== n_html){			
				editor.execCommand('requalitySave', editor);
	        }else{
	        	editor.execCommand('requalityExit', editor);
	        }
        }
        return true;
    });
    editor.on('dialogShow', function (be) {
		// Stop clicks in the dialog from bubbling up to document
		// (Cannot use global live() handler because the bind()'ed event will be called anyway)
		$(CKEDITOR.dialog.getCurrent().getElement().$).find('.cke_dialog').unbind('click.ckeditor').bind('click.ckeditor', function (e) {
			//be.preventDefault();
			//be.stopPropagation();
		});
	});
	
	/*editor.on('key', function (ea){

		// Catch hotkeys pressed inside the editor
		var elem = f.closest('.supported-node')[0];
		if (hotkeyManager.execHotkeyAction(ea, 'handlerCKEditor', elem, extractUUID(elem.id))) {
			//alert(1);
			ea.stopPropagation();
		}
	});*/
	var uuid = extractUUID(elem.id);
	editor.on('beforeCommandExec', function(e){
		if (e && e!=null && e.data.name == 'requalitySave') {
			if(e.editor.status!="ready")
			{
				return false;
			}
			var elem = $(e.editor.element.$);
			var new_data = e.editor.getData();
			if ($(elem).html() == '')
				$(elem).html('&nbsp;');
			var classList = (elem.attr('class')+" "+elem.attr('id')).split(/\s+/);
			for (var i = 0; i < classList.length; i++) {
				var id = classList[i];
				var pos = id.indexOf('-');
				var field_type = id.substr(0, pos);
				var uuid = id.substr(pos + 1);
				if (saveAction[field_type]){
					if(e.editor.checkDirty()){
						saveAction[field_type](uuid, new_data.replace(/&nbsp;/g, " "));//.replace(/\n/g,""));
						return true;
					}
				}
			}
			return false;
		}
		return true;
	});
	editorOpening = false;
	return editor;
}

//Opens editor window in place of the specific DIV element. The previous editor
//instance (if present) is closed (saving all the changes).
//Input parameters:
//elem       - DIV element to be replaced with the editor (if null, editor will not open)
//simplified - whether editor should be a simplified version (for in-place renaming)
//Return value:
//none
function putEditor(elem, simplified) {
  putEditorWithHtmlClean(elem, simplified, false);
}


//Toggles (shows or hides) the block with children requirements.
//Input parameters:
//	uuid - UUID of the parent requirement
//Return value:
//	none
function toggleChildren(uuid, time, scilent) {
	if(typeof time === "undefined")
		time = animationPeriod;
	var req_children_div = $('#children-General-' + uuid);
	var img_elem = $('#allchildren-' + uuid).children('img.plusminus');
	if (req_children_div.is(':hidden')) {
		req_children_div.removeClass("hiddenChilds");
		req_children_div.slideDown(time);
		$(img_elem).attr('src', 'images/collapse.png').attr('alt', '[&minus;]');
	}
	else {
		req_children_div.addClass("hiddenChilds");
		var funct = function () {
			//if ($(all_active_elems[keyboard_focus]).is(':hidden'))
			if(scilent!==true)
				setFocusByUUID(uuid);
		};
		if(time == 0)
			req_children_div.hide(0, funct);
		else
			req_children_div.slideUp(time, funct);
		
		$(img_elem).attr('src', 'images/expand.png').attr('alt', '[+]');
	}
}
//List of HTML block wrapper constructors for different types of nodes
var constructNodeWrapperProcessor = {
	'General': function (uuid) {
		return $('<div id="children-General-' + uuid + '" class="children children-General" />');
	},
};

function emulateTestProcedure()
{
	return window.location.href.charAt(window.location.href.length-1) == '?';
}
	

function getType(uuid){
	if(!uuid)
	  return "global";
	var node = getNodeByUUID(uuid);
	if(typeof node!=="undefined" && node!=null){
	  return node.attributes._type.value;
	}
	return null;
}
	
////////////////////////////////////////////////////////////////////////////////
//Callback functions that can be called by Requality

//Callback function called by Requality when node or some of its children change outside of UniEditor
//Input parameters:
//	uuid - (String) uuid of the updated node
//Return value:
//	none
//function isNodeHeader(uuid){
//	var ctype = getType(uuid); 
//	if(ctype=='Requirement' 
//			|| ctype=='TextNode' || ctype=="VirtualNode"|| ctype=="TestStep"){
//		var child = getNodeByUUID(uuid);
//		if (ctype=="TestStep" || (ctype=='Requirement'|| ctype=="VirtualNode") && child.children.length>0 && 
//		(countChildrenOfType(uuid, "Requirement")+countChildrenOfType(uuid, "TextNode"))>0 ||
//				child.attributes._textNodeType && 
//				child.attributes._textNodeType.value &&
//				child.attributes._textNodeType.value != 'text')
//		 			return true;
//	}
//	return false;
//	
//}


function pollFunc(fn, uuid, timeout, interval) {
    var startTime = (new Date()).getTime();
    interval = interval || 1000,
    canPoll = true;

    (function p() {
        canPoll = ((new Date).getTime() - startTime ) <= timeout;
        if (!fn(uuid) && canPoll)  { 
            setTimeout(p, interval);
        }
    })();
}

var replaces = [];
var foc = null;
function replaceSingleDum(vele){
    var id = extractUUID(vele.attr("id"));
	var tree = constructTree(id, !checkChildsUpdated(id));
	if(vele.hasClass("focused"))
		foc = id;
	if(vele.parent().hasClass("focused"))
		foc = id;
	var newNode = vele.parent().replaceWith(tree);
	if(dragActivateStarted){
		var req = newNode.find('.supported-node');
		updateDroppableState(newNode, id);
		req_elems = $('.supported-node');
		if(typeof(reqHighlight)!=="undefined" && reqHighlight!=null){
			reqHighlight(mousePageX, mousePageY);
		}
	}
}
function replaceDummies(elem1,elem2) {
    foc = null;
	if(!elem2){
		var elems = elem1.find(".dummy");
		if(elem1.hasClass("dummy")){
			elems=[elem1];
		}
		var ch = false;
		Array.prototype.filter.call(elems, function(elem){
			if(elem!=null && typeof elem!=="undefined"){
				//var pos = replaces.indexOf(elem.id);
				//if(pos==-1){
					//replaces.push(elem.id);
					var vele = $(elem);
					if(vele.isInViewport()){
						replaceSingleDum(vele);
						ch = true;
					}
					//replaces.slice(replaces.indexOf(elem.id));
				//}
				//onChangeNode(elem.id,true);
			}
		});
	}else
	{
		replaceSingleDum(elem2);
		ch = true;
	}
	if(ch){
		updateElList();
		if(foc!=null)
			setFocusByUUID(foc);
			
	}
	return false;
//    if (elems.length==0) {
  //      return true; // or false, change the IIFE inside condition accordingly.
    //}
}
function handleDeleted(nodeu, paru){
	if(!nodeu)
		return;
	var domblock = $('#block-'+paru);
	if(domblock.length>0){
		var pos = $.inArray(nodeu, parentMap);
		if(pos>-1)
			parentMap.slice(pos,1);
		var uid = paru;
		const fuid = uid;
		loadNodeThen(paru, function(){
			var node = getNodeByUUID(paru);
			if(node){
				pos = $.inArray(nodeu, node.children);
				if(pos>-1&&node.children){
					node.children.splice(pos, 1);
				}
				if(!node.children||node.children.length<1)
					onChangeNode(fuid,false);
			}
			var domnodea = $('#block-'+nodeu);
			if(domnodea.length>0)
				domnodea.remove();
		});
		
	}
}

function updateChildren(node, nodeChildren, oldSide){
	if(!node)
		return;
	//lazy
	if(!nodeChildren)
		nodeChildren = [];
	else
		nodeChildren = filterChildren(nodeChildren);
	var domwrappers = [];
	for(var name in constructNodeWrapperProcessor){
		var el = document.getElementById('children-'+name+'-'+node);
		if(el!=null){
			domwrappers[name] = $(el);
		}
	}
	//var domnode = $("#reqchildren-"+node);
	var changed = false;
	var order = nodeChildren;
	var dorderside = !oldSide?null:oldSide.length;
	var dorder = !oldSide?[]:oldSide;
	//call children update?
	if(!dorderside){
		$.each( $('#allchildren-'+node).children().children(".block"), function(){
			var id = extractUUID(this.id);
			if(id && isLegit(id))
				dorder.push(id);
		});
		dorderside = dorder.length;
	}
	const fnode = node;
	loadNodeTreeAndLoad(node, function(){
		var desc = getNodeByUUID(fnode);
		if(!desc)
			return;//non exitsting node
		var oldsize = dorderside;
		var oldOrder = dorder;
		if(!oldOrder)
			oldOrder = [];
		var update=false;
		var changeN = false;
		var domblock = $('#allchildren-'+fnode);
		desc.children = order;
		var crdeep=desc.getDeep(),crpath=crpath = desc.attributes._numericId?desc.attributes._numericId.newvalue:"";
		if(!crdeep){
			crdeep = 0;
		}
		var k = 0;
		if(oldsize==order.length){
			var changed = false;
			for(var i=0;i<oldsize&&!changed;i++){
				if(!oldOrder[i]){
					changed = true;
					break;
				}
				var chi = getNodeByUUID(order[i]);
				var isHeader = isNodeHeader(oldOrder[i]);
				if(isHeader)
					k++;
				if(oldOrder[i]!=order[i]){
					changed = true;
					break;
				}
				else
				{
					var rpath2=(crpath&crpath.length>0?(crpath+"."):"")+(isHeader?k:0)+".";
					if(rpath2!=chi.getPath()){
						changed = true;
						break;
					}
				}
			}
			if(!changed){
				return;
			}
		}
		if(oldsize.length==order.length && !changed)
			return;
		keyboard_focus = -1;
		var focused = $('.focused');
		var savedFocus = [];
		focused.each(function() {
          var uid = extractUUID(this.id);
          savedFocus.push(uid);
		});
		focused.removeClass('focused');
		
		var i = 0;
		var docelem = [];
		for(i = 0;i<oldOrder.length;i++){
			docelem[oldOrder[i]] = $('#block-' + oldOrder[i])[0];
			$(docelem[oldOrder[i]]).detach();
		}
		
		if(oldsize==0&&order.length!=0 ||oldsize!=0&&order.length==0){
			desc.children = order;
			desc.path = null;
			//$("allchildren-"+node).remove();
			onChangeNode(fnode,false);
			domblock = $('#allchildren-'+fnode);
			changeN = true;
	
			for(var name in constructNodeWrapperProcessor){
				var el = document.getElementById("children-"+name+"-"+fnode);
				if(el!=null)
					domwrappers[name] = $(el);
			}
			//onChangeNode(node,true);
			//return;
		}
		
		//change required
		j = 0;
		var ne = [];
		var changedp = [];
		var changecount=0; 
		for(i = 0;i<order.length;i++){
			var chiuid = order[i];
			var chi = getNodeByUUID(chiuid);
			var dom = null;
			var isHeader = isNodeHeader(chiuid); 
			if(isHeader)
				j++;
			var name=getType(chiuid);
			if ( synonim[name] )
				name = synonim[name];
			if(!constructNodeWrapperProcessor[name])
				continue;
			var rpath2=crpath+(isHeader?j:0)+".",rdeep2=crdeep+1;
			var container = domwrappers[name];
			if(container == null || typeof container === "undefined"){
				container = constructNodeWrapperProcessor[name](fnode);
				domwrappers[name] = container;
				domblock.append(container);
			}
			ne[name]=true;
			if(docelem[chiuid] == null || docelem[chiuid].length==0){
				var fnd = $('#block-' + chiuid);
				if(fnd.length>0){
					docelem[chiuid] = fnd[0];
					fnd.detach();
				}
			}
			if(docelem[chiuid]!=null && docelem[chiuid]){
				//printToConsole("old "+order[i]+" "+container+" "+docelem[order[i]]);
				var dom = docelem[chiuid];
				docelem[chiuid] = null;
				$(dom).appendTo(container);
				//dom = $(document.getElementById(order[i]));
				var frompath = chi.getPath();
				if(rdeep2!=chi.getDeep()||rpath2!=frompath){
					changedp[changecount++]=chiuid;
				}
				//desc.path=rpath2;
				//desc.deep = rdeep2;
			}else{
				//printToConsole("new "+order[i]);
				//printToConsole(1);
				//updateTree(order[i]);
				loadNodeTreeAndLoad(chiuid, function(){
					dom=constructTree(chiuid,!checkChildsUpdated(chiuid));
					container.append(dom);
					changed = true;
					updateElList();	
				});
				//domnode.appendChild(constructTree(order[i],0,""));
			}
		}
		for(var v in docelem)
		{
			var val = docelem[v];
			if(val){
				val = $(val);
				val.appendTo(domblock);//late remove
				val.remove();
			}
		}
		for (var i = 0; i < childrenNodesOrder.length; ++i) {
			var chldType = childrenNodesOrder[i];
			if (domwrappers[chldType]&&ne[chldType]) {
				domblock.append(domwrappers[chldType]);
				if(chldType == "General"){
					if(domblock.children(".plusminus").length == 0){
						domblock.addClass('collapsable');
						domblock.prepend($('<img src="images/collapse.png" alt="[&minus;]" class="plusminus" onclick="javascript:toggleChildren(\'' + fnode + '\');" />'));
					}
				}
			}
		}
		for(var name in constructNodeWrapperProcessor)
			if(typeof domwrappers[name]!=="undefined"){
				if(!ne[name]){
					domwrappers[name].remove();
					if(name == "General"){
						var pl = domblock.children(".plusminus");
						pl.remove();
	//					domblock.addClass('collapsable');
	//					domblock.prepend($('<img src="images/collapse.png" alt="[&minus;]" class="plusminus" onclick="javascript:toggleChildren(\'' + fnode + '\');" />'));
					}
					//domwrappers[name]=null;
				}//else
					//domwrappers[name].detach();
			}
		//if(changed)
		replaceDummies(domblock);
		updateElList();	
		for (var uid in savedFocus){
		  var one = savedFocus[uid];
		  setFocusByUUID(one, true);
		  break;
		}
//		updatePath(node, true);//update all paths
	});
}
var loadingLocationsMap = {};
function formLocationDesc(nodeData){
	//TODO loading problems
	if (nodeData.attributes._locations) {
		var loc_text = '';
		var loc_new_text = '';
		loadingLocationsMap[nodeData.uuid]=nodeData.attributes._locations.value.length;
		for (var i1 in nodeData.attributes._locations.value) {
			var luid = nodeData.attributes._locations.value[i1];
			loadNodeThen(luid, function(){
				loadingLocationsMap[nodeData.uuid]--;
				if(loadingLocationsMap[nodeData.uuid]>0)
					return;//non exitsting node
				for (var i in nodeData.attributes._locations.value) {
					var locJSON;
					try {
						locJSON = getNodeByUUID(nodeData.attributes._locations.value[i]);
					}
					catch (ex) {
						alert(ex.name+"  "+ex.message+"  "+ex.trace);
						continue;
					}
					if (!locJSON)
						continue;
					if (locJSON.attributes._lhidden && locJSON.attributes._lhidden.value)
						continue;
					var locData = locJSON;
					if (loc_text != '')
						loc_text += ' ';
					if (loc_new_text != '')
						loc_new_text += ' ';
					if (!locData.attributes._hidden||!locData.attributes._hidden.value){
						if(locData.attributes._description && locData.attributes._description.value)
							loc_text += locData.attributes._description.value;//.replace(/\n/g,"");
						if(locData.attributes._description && locData.attributes._description.newvalue)
							loc_new_text += locData.attributes._description.newvalue;//.replace(/\n/g,"");
					}
				}
				nodeData.attributes._ldescription = { 'value': (loc_text == '') ? '&nbsp;' : loc_text ,
				'newvalue':(loc_new_text == '') ? '&nbsp;' : loc_new_text};
				
				var deff = window.attributeUpdate["_vdescription"];
				if(!deff)
					deff = window.attributeUpdate['_default'];
				var des = getDescription(nodeData.uuid);
				deff(nodeData.uuid,"_vdescription",des['value'],des['newvalue']); 
			});
			
		}
	}
	else
		nodeData.attributes._ldescription = { 'value': '&nbsp;','newvalue': '&nbsp;'};
}

function handleAttrUpdate(node, vals){
	loadNodeThen(node, function(){
		var nodeN = getNodeByUUID(node);
		if(!nodeN)
			return;//node unloaded 	 
		if(!nodeN.attributes)
			nodeN.attributes = {};
		var name=vals.key;
		var raw=vals.value;
		if(raw)
			raw = decodeEncodedChars(raw);
		var val=decodeEncodedChars(vals.newvalue);
		var deff = window.attributeUpdate[name];
		if(!deff)
			deff = window.attributeUpdate['_default'];
//		else
	//		printToConsole("nondef ach "+name+" "+raw);	
		deff(node,name,raw,val); 
	});
}
function isLegit(uid){
	var name=getType(uid);
	if ( synonim[name] )
		name = synonim[name];
	if(!constructNodeWrapperProcessor[name])
		return false;
	return true;
}
function filterChildren(childs){
	var ret = [];
	for(var i=0;i<childs.length;i++)
	{
		var uid = childs[i];
		if(isLegit(uid))
			ret.push(uid);
	}
	return ret;
}
function handleEvents(arr){
	if(Array.isArray(arr)){
		var arrayLength = arr.length;
		for (var i = 0; i < arrayLength; i++)
			handleEvents(arr[i]); 
	}
	else{
		try{
			if(arr.op == "moved"){
				var node = arr.node;
				var oldp= arr.oldparent;
				var newp=arr.parent;
				loadNodeThen(node, function(){
					var oldc = null;
					if(typeof oldp!=="undefined"){
						oldc = [];
						$.each( $('#allchildren-'+oldp).children().children(".block"), function(){
							var id = extractUUID(this.id);
							if(id && isLegit(id))
								oldc.push(id);
						});
					}
					var newc = null;
					if(typeof newc!=="undefined"){
						newc = [];
						$.each( $('#allchildren-'+newp).children().children(".block"), function(){
							var id = extractUUID(this.id);
							if(id && isLegit(id))
								newc.push(id);
						});
					}
					getNodeByUUID(node).parent = newp;
					parentMap[node]=newp;
					if(typeof oldp!=="undefined")
						updateChildren(oldp,arr.oldParentChildren, oldc);
					if(typeof newp!=="undefined")
						updateChildren(newp, arr.parentChildren, newc);
					//TODO rewrite
					updatePath(oldp, true);//update node paths
					updatePath(newp, true);//update node paths
				});
				//TODO move
			}else if(arr.op=="deleted"){
			    var node = arr.node;
			    handleDeleted(node, arr.parent);
				
			    //TODO delete
			}else if(arr.op=="updateChildrenOrder"){
				var uuid = arr.node;
				updateChildren(arr.node, arr.children);
				updatePath(uuid, true);//update node paths
				//var node = getNodeByUUID(uuid);
				
			}else if (arr.op == "changeAttributes"){
				var node = arr.node;
				var atatt = arr.values;
				var len = atatt.length;
				for(var j=0;j<len;j++)
					handleAttrUpdate(node, atatt[j]);
			}else if(arr.op == "created"){
				var node = arr.node;
				var par = arr.parent;
				parentMap[node] = par;
				loadNodeTreeAndLoad(node, function(){
					updateChildren(par, arr.parentChildren);
				});
			}
		}
		catch(e){
			alert(e.stack);
		}
	}
}
function onEvents(val){
	//printToConsole("events:'"+val+"'");
	try{
		var arr = $.parseJSON(val);//TODO add getEventData
		handleEvents(arr);
	}catch(e){
		alert(e +"\n"+ e.stack);
	}
	//printToConsole("eventsover:'"+val+"'");
}
function formDeepPath (uuid, id,dom){
	var node = getNodeByUUID(uuid);
	if(!node)
		return null;
	return [Number(node.getDeep())+1,node.getPath()+id+"."];
}
function getParentUUID(uuid){
	var node = getNodeByUUID(uuid);
	if(!node)
		return null;
	return node.parent;//TODO test
}
function getNodeTypeByNode(node){
	if(!node)
		return null;
	if(!node.attributes._type)
		alert("node without type! "+node);
	return node.attributes._type.value;
}
function isNodeHeader(uuid){
	var node = getNodeByUUID(uuid);
	if(!node)
		return false;
    var ctype = getNodeTypeByNode(node); 
    var isReq = ctype == "Requirement";
    var isTxt = ctype == "TextNode";
    var isVN = ctype == "VirtualNode";
    var isTS = ctype == "TestStep";
    if(isReq || isTxt || isVN || isTS){
      //  var child = getNodeByUUID(uuid);
        if (isTS || (isReq || isVN) && countChildren(uuid)>0 && 
        (countRTNChilds(uuid))>0 
                || nodeHeaderCheck(node))
                    return true;
    }
    return false;
}
function nodeHeaderCheck(node){
	var attr = node.attributes["Вид"];
	if(attr && attr.value)
		return attr.value=="Заголовок";
	return false;
}
function countRTNChilds(uuid){
	//TODO single call?
	return countChildrenOfType(uuid, "Requirement")+ countChildrenOfType(uuid, "TextNode");
}


function onChangeNode(uuid, silent) {
	var local = false, last = null;	
	nodeToFocus = selectedNode;
	if(!silent && countEditorInstances()>0 && !last){
		local = true;
		for(var name in CKEDITOR.instances){
			var editor = CKEDITOR.instances[name];	
			if(!editor)
				continue;
			last = editor.element.$.id;
			if(editor.status != "destroyed"){
				if(editor.checkDirty())
					editor.execCommand('requalitySave');
				else
					editor.execCommand('requalityExit');
			}
		}
	}
	//var pdef = getNodeByUUID(parent[uuid]);
	var rdeep = 0,rpath="",id = -1, j = 0;
	var jsparui = getParentUUID(uuid), jspar;
	if(jsparui)
		jspar = getNodeByUUID(jsparui);
	//todo locate id

	//var rdeep=ret!=null?ret[0]:null,rpath=ret!=null?ret[1]:null;

	updateTree(uuid);


	if(nodeToFocus != null){
//		setFocusByUUID(nodeToFocus);
		nodeToFocus = null;
	}
	else{
		if(last && local){

			$("#"+last).click();
			last = null;
		}
	}
}

//Callback function called by Requality when it requests UniEditor to change focus
//Input parameters:
//	uuid - (String) uuid of the node to set focus to
//Return value:
//	none
function onChangeFocus(uuid) {
	// If this is a callback because of setting focus from UniEditor, ignore it

	// Check whether the focus is already on the requested element
	if (all_active_elems==null || all_active_elems[keyboard_focus]&& uuid  == extractUUID(all_active_elems[keyboard_focus].id))
		return;
	// Search for the requested element
	if(!uuid){
		setFocus(null,true);
		return;
	}
	var new_elem = $('#node-' + uuid+", #dummy-"+uuid);
	if (new_elem.length == 0)
		return;
	if (new_elem.is(':hidden')) {
		// Expand all subtrees from the current element up to the root
		new_elem.parents('.hiddenChilds').each(function () {
			var node_uuid = extractUUID(this.id);
			if ($('#children-General-' + node_uuid).is(':hidden'))
				toggleChildren(node_uuid);
		});
		// Wait for all trees to complete expanding, then set focus
		// (otherwise the element will have wrong coordinates and cannot be scrolled to view)
		setFocus(new_elem, true);
	}
	else {
		setFocus(new_elem, true);
		// The desired element is already visible, set the focus immediately
		//setFocus(new_elem, true);
	}
}


var readOnlyClasses = {};
readOnlyClasses[EDIT_MODE_REQ_RO] = 'requirement';
readOnlyClasses[EDIT_MODE_TN_RO] = 'textnode';


function isReadOnly(elem){
	for (var flag in readOnlyClasses) {
		if (((window.readOnlyMode & flag) != 0) && elem.hasClass(readOnlyClasses[flag]))
			return true;
	}
	return window.readOnly;	
}
function getUpdatingState(){
	return isUpdating;
}
// Checks whether the specified element is allowed to edit
// Input parameters:
//	elem - jQuery object with the element to check
// Return value:
//	true if element is editable; false otherwise
function isEditable(elem) {
	if(isReadOnly(elem))
		return false;
	return !getUpdatingState();
}

// Extracts the UUID from DOM element ID
// Input parameters:
//	id - identifier in the form 'smth-XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX'
//	     (UUID with some prefix)
// Return value:
//	UUID extracted from the ID (prefix removed)
function extractUUID(id) {
	if(typeof id == "undefined")
		return null;
	//var id = id.substr(id.indexOf('-') + 1);;
	var cnt = 0;
	var ind = id.indexOf("-");
	var poss=[];
	while(ind>-1){
		poss[cnt++]=ind;
		ind = id.indexOf("-",ind+1);
	}
	var j = 0;
	for(var i = 1;i<cnt;i++)
		if(poss[i]-poss[i-1]==5){
			j++;
			if(j == 3)
				return id.substr(poss[i]-23,poss[i]+13);
		}else
			j = 0;
	return null;
}

// Changes the focus to the new element
// Input parameters:
//	elem     - jQuery object with the element to be focused
//	silently - [optional] if true, do not notify Requality about changes
// Return value:
//	none
function setFocus(elema, silently) {
	//var ela = $(elema);
	//if(!ela.hasClass('supported-node')&&!ela.hasClass('dummy'))
		//ela = ela.closest('.supported-node,.dummy');
	try{
		if(!all_active_elems)
			return;
		if(!elema){
			if(keyboard_focus>-1){
				$(all_active_elems[keyboard_focus]).removeClass('focused');
				keyboard_focus = -1;
			}
			return;
		}
		
		var ind = all_active_elems.index(elema);
		for(var i = ind-10;i<=ind+10;i++){
			var elem = $(all_active_elems[i]);
			if(elem.hasClass("dummy")){
				replaceDummies(elem.parent(),elem);
			}
		}
		elema = all_active_elems[ind];
		var el = $(elema);
		if(!$(elema).hasClass('supported-node'))
			el = $(elema).closest('.supported-node');
		if(keyboard_focus>-1)
			$(all_active_elems[keyboard_focus]).removeClass('focused');
		keyboard_focus = all_active_elems.index(el);
		if(el.hasClass('focused'))
			return;
		if (!silently && elema!=null) {
			var uuid = extractUUID(elema.id?elema.id:elema[0].id);
			setRequalityFocus(uuid);
			selectedNode = uuid;
		}
	//	for(var name in CKEDITOR.instances){
	//		var editor = CKEDITOR.instances[name];
	//		if(editor  && editor.status=="ready"){
	//			if(!closeStarted){
	//				printToConsole("save "+name);
	//				saveEditor();
	//			}
	//		}	
	//	}
		hideMenu();
		el.addClass('focused');
		if(!silently)
			setTimeout(function () {
				el.scrollToView();
			}, animationPeriod);
	}
	catch(e){
		alert(e+" "+e.stack);
	}
}

// Changes the focus to the new element specified by the index
// Input parameters:
//	idx      - index of the element to be focused
//	silently - [optional] if true, do not notify Requality about changes
// Return value:
//	none
function setFocusByIndex(idx, silently) {
	var new_elem = $(all_active_elems[idx]);
	if(keyboard_focus>-1)
		$('.focused').removeClass('focused');
	keyboard_focus = idx;
	var el = $(all_active_elems[keyboard_focus]).addClass('focused');
	el.scrollToView();
	if (!silently) {
		var uuid = extractUUID(all_active_elems[keyboard_focus].id);
		setRequalityFocus(uuid);
	}
	hideMenu();
}

// Changes the focus to the new element specified by the UUID
// Input parameters:
//	uuid     - UUID of the element to be focused
//	silently - [optional] if true, do not notify Requality about changes
// Return value:
//	none
function setFocusByUUID(uuid, silently) {
	var new_elem = $('#node-' + uuid+', #dummy-'+ uuid);
	if(keyboard_focus>-1)
		$(all_active_elems[keyboard_focus]).removeClass('focused');
	if(new_elem!=null)
		setFocus(new_elem, silently);
//	keyboard_focus = all_active_elems.index(new_elem);
//	$(all_active_elems[keyboard_focus]).addClass('focused').scrollToView();
//	if (!silently) {
//		setRequalityFocus(uuid);
//	}
//	hideMenu();
	//TODO test
	//if(editor){
	//	saveEditor();
	//}
}


//List of available commands for all node types
var node_commands = {
	"global":{
		'openTemplates':{
			image: 'templates.png',
			imageAlt: '[Tm]',
			menu: true,
			name: 'Open Templates Editor',
			handler: openTemplates,
			readOnlyMode: 0
		}
	},
	'Requirement': {
		'addTest': {
			menu: true,
			name: 'Add Test',
			image: 'dnd-copy.png',
			imageAlt: '[T]',
			readOnlyMode: EDIT_MODE_VIRTUAL,
			handler: callAddTest
		},
		'delreq': {
			menu: true,
			name: 'Delete Requirement',
			image: 'delete-node.png',
			imageAlt: '[&minus;]',
			readOnlyMode: EDIT_MODE_REQ_RO,
			handler: deleteNodeConfirm
		},
		'editNode':{
			menu: true,
			name: 'Open Editor',
			image: 'req-status-complete.png',
			imageAlt: '[E]',
			readOnlyMode: EDIT_MODE_REQ_RO,
			handler: editNode
		},
//		'editattr': {
//			menu: true,
//			name: 'Edit attributes',
//			image: 'edit-attrs.png',
//			imageAlt: '[&equiv;]',
//			readOnlyMode: 0,
//			handler: function (uuid) {}
//		},

		'addreqsib': {
			menu: true,
			name: 'Add sibling Requirement',
			createdType: 'Requirement',
			image: 'add-req.png',
			imageAlt: '[+]',
			readOnlyMode: EDIT_MODE_REQ_RO|EDIT_MODE_VIRTUAL,
			handler: addSiblingRequirementConfirm
		},
		'addreqchld': {
			menu: true,
			name: 'Add child Requirement',
			createdType: 'Requirement',
			image: 'add-req.png',
			imageAlt: '[+]',
			readOnlyMode: EDIT_MODE_REQ_RO|EDIT_MODE_VIRTUAL,
			handler: addChildRequirementConfirm
		},
		'addtnsib': {
			menu: true,
			name: 'Add sibling Text Node',
			createdType: 'TextNode',
			image: 'add-req.png',
			imageAlt: '[+]',
			readOnlyMode: EDIT_MODE_REQ_RO|EDIT_MODE_VIRTUAL|EDIT_MODE_TN_RO,
			handler: addSiblingTextNodeConfirm
		},
		'addtnchld': {
			menu: true,
			name: 'Add child Text Node',
			createdType: 'TextNode',
			image: 'add-req.png',
			imageAlt: '[+]',
			readOnlyMode: EDIT_MODE_REQ_RO|EDIT_MODE_VIRTUAL|EDIT_MODE_TN_RO,
			handler: addChildTextNodeConfirm
		},
		'addcmt': {
			menu: true,
			name: 'Add new Comment',
			createdType: 'Comment',
			image: 'add-cmt.png',
			imageAlt: '[+]',
			readOnlyMode: EDIT_MODE_VIRTUAL,
			handler: addComment
		},
		'chstatus': {
			menu: true,
			image: 'status.png',
			name: 'Change status',
			imageAlt: '[S]',
         	readOnlyMode: 0,
			enableFunc: window.editorName=='unieditor',
			handler: changeStatus
		},
		'chtype': {
			menu: true,
			image: 'switch.png',
			imageAlt: '[Tp]',
			name: 'Switch type to Text Node',
			readOnlyMode: EDIT_MODE_REQ_RO|EDIT_MODE_VIRTUAL|EDIT_MODE_TN_RO,
			handler: switchRequirementType
		}
	},
	'TextNode': {
		'addTest': {
			menu: true,
			name: 'Add Test',
			image: 'dnd-copy.png',
			imageAlt: '[T]',
			readOnlyMode: EDIT_MODE_VIRTUAL,
			handler: callAddTest
		},
		'delreq': {
			menu: true,
			name: 'Delete Text Node',
			image: 'delete-node.png',
			imageAlt: '[&minus;]',
			readOnlyMode: EDIT_MODE_TN_RO,
			handler: deleteNodeConfirm
		},
		'editNode':{
			menu: true,
			name: 'Open Editor',
			image: 'req-status-complete.png',
			imageAlt: '[E]',
			readOnlyMode: EDIT_MODE_TN_RO,
			handler: editNode
		},
//		'editattr': {
//			menu: true,
//			name: 'Edit attributes',
//			image: 'edit-attrs.png',
//			imageAlt: '[&equiv;]',
//			readOnlyMode: 0,
//			handler: function (uuid) {}
//		},

		'addreqsib': {
			menu: true,
			name: 'Add sibling Requirement',
			createdType: 'Requirement',
			image: 'add-req.png',
			imageAlt: '[+]',
			readOnlyMode: EDIT_MODE_REQ_RO|EDIT_MODE_VIRTUAL|EDIT_MODE_TN_RO,
			handler: addSiblingRequirementConfirm
		},
		'addtnsib': {
			menu: true,
			name: 'Add sibling Text Node',
			createdType: 'TextNode',
			image: 'add-req.png',
			imageAlt: '[+]',
			readOnlyMode: EDIT_MODE_REQ_RO|EDIT_MODE_VIRTUAL|EDIT_MODE_TN_RO,
			handler: addSiblingTextNodeConfirm
		},
		'addtnchld': {
			menu: true,
			name: 'Add child Text Node',
			createdType: 'TextNode',
			image: 'add-req.png',
			imageAlt: '[+]',
			readOnlyMode: EDIT_MODE_REQ_RO|EDIT_MODE_VIRTUAL|EDIT_MODE_TN_RO,
			handler: addChildTextNodeConfirm
		},
		'addcmt': {
			menu: true,
			name: 'Add new Comment',
			createdType: 'Comment',
			image: 'add-cmt.png',
			imageAlt: '[+]',
			readOnlyMode: EDIT_MODE_VIRTUAL,
			handler: addComment
		},
		'chstatus': {
			image: 'tnstatus.png',
			imageAlt: '[TT]',
         	menu: true,
         	name: 'Change TextNode type',
         	readOnlyMode: 0,
         	enableFunc:  window.editorName=='unieditor',
         	handler: changeStatus
    	 },
		'chtype': {
			image: 'switch2.png',
			imageAlt: '[Tp]',
			menu: true,
			name: 'Switch type to Requirement',
			readOnlyMode: EDIT_MODE_REQ_RO|EDIT_MODE_VIRTUAL,
			handler: switchRequirementType
		}
	},

	'Comment': {
		'delcmt': {
			menu: true,
			name: 'Delete Comment',
			image: 'delete-node.png',
			imageAlt: '[&minus;]',
			readOnlyMode: 0,
			handler: deleteNodeConfirm
		},
		'editNode':{
			menu: true,
			name: 'Open Editor',
			image: 'req-status-complete.png',
			imageAlt: '[E]',
			readOnlyMode: EDIT_MODE_REQ_RO,
			handler: editNode
		},
		'addcmt': {
			menu: true,
			name: 'Add new Comment',
			createdType: 'Comment',
			image: 'add-cmt.png',
			imageAlt: '[+]',
			readOnlyMode: EDIT_MODE_VIRTUAL,
			handler: addComment
		}
	}
};
function getReadOnlyModeOnNode(uuid){
	if(window.readOnlyMode)
		return true;
	var node = getNodeByUUID(uuid);
	if(window.readOnly)
		return true;
	if(node)
		return node.attributes._type=="VirtualNode";//TODO check
	return false;
}

//Executes the menu command for the specific node
//Input parameters:
//	node_type - type of the target node
//	uuid      - UUID of the node
//	cmdId     - command to execute
//Return value:
//	none
function execNodeCommand(node_type, uuid, cmdId) {
    if(typeof uuid === "undefined" || uuid == null)
      uuid = null;
    if(typeof node_type === "undefined" ||node_type==null){
      if(uuid == null)
          node_type = "global";
      else
          node_type=getType(uuid);
    } 
	var rmode = uuid!=null?(window.readOnlyMode || getReadOnlyModeOnNode(uuid)):false;
	var cmd = node_commands[node_type][cmdId];
	if (cmd && ((cmd.readOnlyMode & rmode) == 0)) {
		cmd.handler.call(uuid!=null?$('#node-' + uuid)[0]:null, uuid);
	}else
		{
			var cmd = node_commands["global"][cmdId];
			if (cmd && ((cmd.readOnlyMode & rmode) == 0)) 
				cmd.handler.call(uuid!=null?$('#node-' + uuid)[0]:null, uuid);
		}
}
//Constructs the HTML block for drop-down menu of the specific node
//Input parameters:
//	node_type - type of the target node
//	uuid      - UUID of the node
//Return value:
//	String value containing the HTML code for the drop-down menu block
function constructMenu(node_type, uuid) {
  constructHotMenu(uuid);
  //fallback to hotkey menu
}

//Order in which the children node of different types are appended
var childrenNodesOrder = [ 'Comment', 'General' ];
var synonim = {'Undefined':'General',
				'Requirement':'General',
				'TextNode':'General',
				'VirtualNode':'General'};

function getDescription(uuid){
	var nodeData = getNodeByUUID(uuid);
	var desc = null,desc2 = null;;
	if((nodeData.attributes._description && nodeData.attributes._description.newvalue && 
			nodeData.attributes._description.newvalue != ""
		&& nodeData.attributes._description.newvalue != "&nbsp;")){
			desc=nodeData.attributes._description.newvalue.replace(/&nbsp;/g, " ");//.replace(/\n/g,"<br/>");
			desc2=nodeData.attributes._description.value.replace(/&nbsp;/g, " ");//.replace(/\n/g,"<br/>");
		}
			//desc = nodeData.attributes._description.newvalue.replace(/&nbsp;/g, " ").replace(/\n/g,"<br/>");
	if(!desc&&(nodeData.attributes._ldescription && nodeData.attributes._ldescription.newvalue )){
		desc=nodeData.attributes._ldescription.newvalue.replace(/&nbsp;/g, " ");//.replace(/\n/g,"");
		desc2=nodeData.attributes._ldescription.value.replace(/&nbsp;/g, " ");//.replace(/\n/g,"");
	}
	if(!desc){
		desc= "&nbsp;";
		desc2= "&nbsp;";
		}
	desc = desc.replace(/\\\"/g,"\"");
	desc2 = desc2.replace(/\\\"/g,"\"");
	return {'newvalue':desc,'value':desc2};
}
function getName(node){
	if(node.attributes._name&&node.attributes._name.value){
		var name = node.attributes._name;
		if(!name.value)
			name.value="";
		if(!name.newvalue)
			name.newvalue=name.value;
		return {'newvalue':name.newvalue.toString().replace(/  /g,"&nbsp;&nbsp;"),'value':name.value.toString().replace(/  /g,"&nbsp;&nbsp;")};
	}
	return null;
}
function getNameOrId(node){
	var name = getName(node);
	if(name!=null && name.newvalue.length>0)
		return name;
	return {'newvalue':node.attributes._id.newvalue.toString().replace(/  /g,"&nbsp;&nbsp;"),'value':node.attributes._id.value.toString().replace(/  /g,"&nbsp;&nbsp;")};
}

//Reads the tree contents starting from UUID specified and updates the document
//Input parameters:
//	uuid - UUID of the root element of the updated subtree
//Return value:
//	none
var repl = null;
function constructDummyTree(uuid){
	var ed = document.createElement('div');
	ed.id = "block-"+uuid;
	ed.className = "block";
	var ecd = $(ed);
	//var node = $("<div/>",{
	var e = document.createElement('div');
	e.id = "dummy-"+uuid;
	e.className = 'dummy supported-node';
	var ec = $(e);
	loadNodeThen(uuid, function(){
		var nodeData = getNodeByUUID(uuid);
		//e.innerHTML = nodeData.attributes._uservisibleid.newvalue
		var tp = nodeData.attributes._type.value;
		if(tp)
			ec.type = tp;
		else
			ec.type = 'Undefined';
		ecd.type = ec.type;
		var cons = constructNodeChildren[ec.type];
		var newC;
		if (cons) {
			newC = cons(nodeData);
		}
		else {
			newC = constructNodeChildren["_default"](nodeData);
		}
		if(nodeData.children.length>0)
			ec.append(newC);
	});
	ecd.append(ec);
	return ecd;
}
function updateDroppableState(newNode, uuid){
	newNode.removeClass('dropable');
	if(cachedDropTargets!=null && cachedDropTargets.length>0){
		var possibleDrop = cachedDropTargets.includes(uuid);
		if(!possibleDrop){
			newNode.addClass("disabled");
			newNode.addClass("undropable");
		}
	}
}
  $.fn.isInViewport = function() {
	  var elementTop = $(this).offset().top;
	  var elementBottom = elementTop + $(this).outerHeight();

	  var viewportTop = $(window).scrollTop();
	  var viewportBottom = viewportTop + $(window).height();

	  return elementBottom > viewportTop && elementTop < viewportBottom;
  };
  if(typeof constructNodeProcessor === "undefined")
 	constructNodeProcessor = [];
  function checkChildsUpdated(uuid){
	var nodeData = getNodeByUUID(uuid);
	var cc= $(document.getElementById("allchildren-"+nodeData.uuid));
	var gathered = [];
	//TODO we need to check childs
	return true;
	/*if(cc.length>0)
	  for(var ccKey in cc){
	    var container = cc[ccKey];
	    if(typeof container.children!="undefined" && container.children.length>0)
	      for(var j in container.children){
	        var childCont = container.children[j];
	        if(typeof childCont.children != "undefined" && childCont.children.length>0)
	          for(var k in childCont.children){
  	            var child = childCont.children[k];
	            var uid = child.id;
	            if(typeof uid != "undefined" && uid.startsWith("uuid-")){
  			      uid = uid.subString(5);
	              gathered.push(uid);
	            }
	          }
	      }
	  }
	return true;*/
  }
  function constructTree(uuid,useold) {
	// Obtain the node data (parse output as JSON string)
	var e = document.createElement('div');
	e.id = "block-"+uuid;
	e.className = "block";
	var ec = $(e);
	
	var nodeData = getNodeByUUID(uuid);
	
	var tp = nodeData.attributes._type.value;
	
	var constr = constructNodeProcessor[tp];
	var newNode;
	if (constr) {
		try{
			newNode = constr(nodeData, uuid);
			newNode.type = nodeData.attributes._type.value;
		}catch(e){
			alert(e.stack);
		}
	}
	else {
		newNode = $();
		newNode.type = 'Undefined';
	}
	if(dragActivated){
		updateDroppableState(newNode, uuid);
	}
	var newC = null;
	if(useold){
		var cc= $(document.getElementById("allchildren-"+nodeData.uuid));
		if(cc.length>0)
			newC = cc[0];
	}
	newNode.uuid = newNode.id;
	if(newC==null){
		var cons = constructNodeChildren[tp];
		var newC;
		if (cons) {
			newC = cons(nodeData);
		}
		else {
			newC = constructNodeChildren["_default"](nodeData);
		}
	}
	newNode.find('.dummychildren').each(function(elem){
			var vele = $(this);
			vele.replaceWith(newC);
		});
	ec.append(newNode);
	return ec;
}

function clearSelection(){
	if (countEditorInstances()>0)
		for(var name in CKEDITOR.instances)
			CKEDITOR.instances[name].selection.collapse();
	if (window.getSelection) {
		window.getSelection().removeAllRanges();
	} else {
	    document.selection.empty();
	}
}
function updateElList(){
	all_active_elems = $('.supported-node');
	all_editable_elems = $('.supported-node .reditable');
}
//Reads the tree contents starting from UUID specified and updates the document
//Input parameters:
//	uuid - UUID of the root element of the updated subtree
//Return value:
//	none
function updateTree(uuid,deep,path) {
	// Remember the focused item and its nearest neighbour
	if(countEditorInstances()>0)
		closeEditor();
	var focused_id;
	var prev_id;
	if (all_active_elems && keyboard_focus>-1) {
		focused_id = all_active_elems[keyboard_focus].id;
		if (keyboard_focus > 0)
			prev_id = all_active_elems[keyboard_focus - 1].id;
	}
	const fuid = uuid;
	loadNodeThen(uuid, function(){
		var uuid = fuid;
		var nd = getNodeByUUID(uuid);
		if(!deep)
			deep = nd.getDeep();
		if(!path)
			path = nd.getPath();
		// Create the updated tree contents
		var newNode = constructTree(uuid, !checkChildsUpdated(uuid));
		// Insert the new tree into the document
		var currentNode = $('#block-' + uuid);
		var isNew = false;
		if (!currentNode.length) {
			// Node does not exist, append it into the contents block
			newNode.appendTo('#contents');
			if(!newNode.is(":visible"))
				{
					newNode.parents(".hiddenChilds").each(function(){
						var uuid = extractUUID(this.id);
						toggleChildren(uuid, 0, true);		
					});
				}
			isNew = true;
		}
		else {
			var hidden = [];
			var hcount = 0;
			currentNode.find('.hiddenChilds').each(function(ind,elem){
				if(elem.id!=""){
					hidden[hcount++] = elem.id;
				}
			});
			if(currentNode.hasClass(".hiddenChilds"))
				hidden[hcount++] = currentNode.id;
			// Node exists, replace it with the new data
			currentNode.replaceWith(newNode);
			for(var i = 0;i<hcount;i++){
				var uuid = extractUUID(hidden[i]);
				toggleChildren(uuid, 0, true);
			}
		}
//		$('.extraAttrs').scrollToFixed({
//			 marginTop: $('#toolsPanel').outerHeight(true) + 10,
//               limit: function() {
//               	var node = $(this).closest('.supported-node'); 
//                   var limit = 0;
//                   limit = node.offset().top + node.outerHeight() - $(this).outerHeight(true)-10;
//                   return limit;
//               },
//               preFixed: function() {
//               	var node = $(this).closest('.supported-node'); 
//               },
//               zIndex: 998
//           });
		// Update the lists of elements
		updateElList();
		// Search for the element that was focused before updating
		var keyboard_focus_new = 0;
		if (focused_id) {
			// Trying to find the focused element
			keyboard_focus_new = all_active_elems.index($('#' + focused_id));
			if ((keyboard_focus_new == -1) && prev_id) {
				// Not found (maybe, removed?), searching for the neighbour
				keyboard_focus_new = all_active_elems.index($('#' + prev_id));
			}
			if (keyboard_focus_new == -1) {
				// Not found either, go to the root
				// TODO: Set focus at the first existing parent
				keyboard_focus_new = 0;
			}
		}
		if(!isNew && countEditorInstances()>0)
			return;
		clearSelection();
//		setFocusByIndex(keyboard_focus_new, true);
		hideMenu();
	});
}

//New jQuery objects method: dimensions()
//Combines all the dimensions of the node into single object
//Input parameters:
//	none
//Return value:
//	Object with element dimensions (left, right, top, bottom - position; width, height - size)
$.fn.dimensions = function () {
	var off = $(this).offset();
	if (!off)
		off = {left: 0, top: 0};
	off.width = this.outerWidth() || this.width();
	off.height = this.outerHeight() || this.height();
	off.right = off.left + off.width;
	off.bottom = off.top + off.height;
	return off;
};

//New jQuery objects method: scrollToView()
//Scrolls the window so that the element became visible()
//Input parameters:
//	none
//Return value:
//	this (allowing for chained calls)
var oldScrollTarget = 0;
var animateCall = 0;
var scrollAttempt = 0;
function scrollToBlock(uid) {
    scrollAttempt++;
    var callForNow = animateCall;
    var old = $("#block-" + uid);
    if (!old)
      return;
	oldScrollTarget = (!old||!old.position()||!old.position().top)?0:old.position().top-MENU_OFFSET;
    var animateTarg = $('html, body');
    // Проверка на видимость элемента перед выполнением скролла
    if (isElementInViewport(old)) {
        return;
    }

    animateTarg.stop();
    animateTarg.animate({
        scrollTop: oldScrollTarget
    }, {
        duration: 500,
        complete: function() {
			if(animateCall==callForNow)
                setTimeout(function() {
                    old = $("#block-" + uid);
					if(!old||!old.position()||!old.position().top)
						return;
					if(Math.abs(old.position().top-MENU_OFFSET-oldScrollTarget)>2)
                        scrollToBlock(uid); // recall
                }, 50);
            }
    });
}

function isElementInViewport(el) {
    var rect = el[0].getBoundingClientRect();
    return (
        rect.top >= 0 &&
        rect.left >= 0 &&
        rect.bottom <= $(window).height() &&
        rect.right <= $(window).width()
    );
}
$.fn.scrollToView = function () {//тут скролл
	try{
		if(this.length>0){
			animateCall++;
			var node = this;
			var parent = this.parents(".block");
			var uid = extractUUID(parent[0].id);
			scrollAttempt = 0;
			scrollToBlock(uid);
		}
	}
	catch(e){
		alert(e.toString()+" "+e.stack);
	}
	//this.scrollTop();
	return this;
};

//Mapping of HTML class names to Requality node types
var nodeClassToType = {
	'requirement': 'Requirement',
	'comment': 'Comment',
	'textnode': 'TextNode'
};

//Converts HTML class name of the node into Requality node type
//Input parameters:
//	elem - jQuery object with the element to check
//Return value:
//	name of the node type, or null if type is unknown
function getNodeType(elem) {
	for (var className in nodeClassToType)
		if (elem.hasClass(className))
			return nodeClassToType[className];
	return null;
}

////////////////////////////////////////////////////////////////////////////////
//Commands that can be used for hotkey assignments

/*
Calling conventions:
Each function has the following signature:

	function myCommand(uuid) {
		do_something();
	}

The input argument uuid contains the UUID of the target node. Target node is the focused
element (if the command was called from keyboard), or the active element (if the command
was called from the drop-down element's menu).
Inside the function you can also use 'this' which points to the target DOM element itself.
*/

//Command function: Switch to the previous editable item
function editPrevious(/* uuid */) {
	if (countEditorInstances()==0)
		return;
	for(var name in CKEDITOR.instances)
		if(CKEDITOR.instances[name].config.bodyClass=='miniCKE')
			return;
	var editor = CKEDITOR.currentInstance;
	if(!editor || editor == null || editor.status =="destroyed")
		return;
	var pos = all_editable_elems.index(editor.element.$);
	if (pos == 0)
		pos = all_editable_elems.length - 1;
	else
		--pos;
	setFocus($(all_editable_elems[pos]).parent());
	putEditor(all_editable_elems[pos], false);
}

//Command function: Switch to the next editable item
function editNext(/* uuid */) {
	if (countEditorInstances()==0)
		return;
	for(var name in CKEDITOR.instances)
		if(CKEDITOR.instances[name].config.bodyClass=='miniCKE')
			return;
	var editor = CKEDITOR.currentInstance;
	if(!editor || editor == null || editor.status =="destroyed")
		return;
	var pos = all_editable_elems.index(editor.element.$);
	if (pos == all_editable_elems.length - 1)
		pos = 0;
	else
		++pos;
	setFocus($(all_editable_elems[pos]).parent());
	putEditor(all_editable_elems[pos], false);
}

//Command function: Close editor discarding the changes
function closeEditor(/* uuid */) {
	// TODO: Calling requalitySave/requalityExit causes errors!
	// Temporarily wrapped into try-catch block to allow script to continue executing.
	var i=0;
	var arr=[];
	for(var name in CKEDITOR.instances)
	{
		try{
			arr[i++]=CKEDITOR.instances[name];
		}
		catch (ex){alert(ex.name+" "+ex.message+" "+ex.trace);}
	}
	var ed = CKEDITOR.currentInstance;
	if(ed && ed != null && e.status !="destroyed")
		arr[i++]=ed;
	for(var j=0;j<i;j++){
		try{
			var editor = arr[j];
			if(editor.status !="destroyed"){
				try{
				  editor.execCommand('requalityExit');
				}catch(e){
					alert(e);
				}
			}
			//editor.destroy();
		}
		catch (ex){printToConsole(ex.name+" "+ex.message+" "+ex.trace);}
	}
}

//Command function: Close editor saving all the changes
function saveEditor(id/* uuid */) {
	// TODO: Calling requalitySave/requalityExit causes errors!
	// Temporarily wrapped into try-catch block to allow script to continue
	// executing.
	var i=0;
	var arr=[];
	for(var name in CKEDITOR.instances)
	{
		try{
			arr[i++]=CKEDITOR.instances[name];
		}
		catch (ex){alert(ex.name+" "+ex.message+" "+ex.trace);}
	}
	var ed = CKEDITOR.currentInstance;
	if(ed && ed != null && ed.status !="destroyed")
		arr[i++]=ed;
	for(var j=0;j<i;j++){
		try{
			var editor = arr[j];
			if(editor.status !="destroyed"){
				try{
					if(editor.checkDirty())
						editor.execCommand('requalitySave');
					else	
						editor.execCommand('requalityExit');
				}catch(e){
					alert(e);
				}
			}
		}
		catch (ex){alert(ex.name+" "+ex.message+" "+ex.trace);}
	}
}

function stacktrace() {
    var err = new Error();
    return err.stack;
}
//Command function: Move focus to the previous focusable node
function focusPrevious(/* uuid */) {
	var keyboard_focus_new = keyboard_focus;
	while (--keyboard_focus_new >= 0) {
		if ($(all_active_elems[keyboard_focus_new]).is(':visible')) {
			setFocusByIndex(keyboard_focus_new);
			return;
		}
	}
	// If failed to go to change active element, scroll to the current one
	$(this).scrollToView();
}

//Command function: Move focus to the next focusable node
function focusNext(/* uuid */) {
	var keyboard_focus_new = keyboard_focus;
	while (++keyboard_focus_new <= all_active_elems.length - 1) {
		if ($(all_active_elems[keyboard_focus_new]).is(':visible')) {
			setFocusByIndex(keyboard_focus_new);
			return;
		}
	}
	// If failed to go to change active element, scroll to the current one
	$(this).scrollToView();
}

function hasVisibleChildren(uuid){
	var nd = getNodeByUUID(uuid);
	if(nd && nd.children)
		for(var uid in nd.children){
			var ct = getType(nd.children[uid]);
			if(allowedTypes.indexOf(ct)>-1)
				return true;
		}
	return false;
}            
//Command function: Collapse node or move to the parent node
function navigateLeft(uuid) {
	if ($(this).parent().hasClass('collapsable')) {
		if ($(this).parent().children('.children-General-'+uuid+'').is(':visible')) {
			toggleChildren(uuid);
		}
		else {
			setFocus($($(this).closest('.collapsable').parent().closest('.collapsable').children('.requirement,.textnode')[0]));
			return;
		}
	}
	else {
		setFocus($($(this).closest('.collapsable').children('.requirement,.textnode')[0]));
		return;
	}
	$(this).scrollToView();
}

//Command function: Expand node or move to the next focusable node
function navigateRight(uuid) {
	if ($(this).parent().hasClass('collapsable') && $(this).parent().children('.children-General-'+uuid+'').is(':hidden')) {
		toggleChildren(uuid);
	}
	else {
		var keyboard_focus_new = keyboard_focus;
		while (++keyboard_focus_new <= all_active_elems.length - 1) {
			if ($(all_active_elems[keyboard_focus_new]).is(':visible')) {
				setFocusByIndex(keyboard_focus_new);
				return;
			}
		}
	}
	$(this).scrollToView();
}

//Command function: Expand the current node
function expandNode(uuid) {
	if ($(this).parent().hasClass('collapsable') && $(this).parent().children('.children-General-'+uuid+'').is(':hidden')) {
		toggleChildren(uuid);
	}
	$(this).scrollToView();
}

//Command function: Collapse the current node
function collapseNode(uuid) {
	if ($(this).parent().hasClass('collapsable') && $(this).parent().children('.children-General-'+uuid+'').is(':visible')) {
		toggleChildren(uuid);
	}
	$(this).scrollToView();
}

//Command function: Move focus to the element located one page above the current one
function pageUp(/* uuid */) {
	var keyboard_focus_new = keyboard_focus;
	var keyboard_focus_tmp = keyboard_focus;
	var new_pos = $(this).position().top - $(window).height();
	while (--keyboard_focus_tmp >= 0) {
		var elem_tmp = $(all_active_elems[keyboard_focus_tmp]);
		if (elem_tmp.is(':visible')) {
			keyboard_focus_new = keyboard_focus_tmp;
			if (elem_tmp.position().top <= new_pos)
				break;
		}
	}
	if (keyboard_focus_new != keyboard_focus) {
		setFocusByIndex(keyboard_focus_new);
		return;
	}
	$(this).scrollToView();
}

//Command function: Move focus to the element located one page below the current one
function pageDown(/* uuid */) {
	var keyboard_focus_new = keyboard_focus;
	var keyboard_focus_tmp = keyboard_focus;
	var new_pos = $(this).offset().top + $(window).height();
	while (++keyboard_focus_tmp <= all_active_elems.length - 1) {
		var elem_tmp = $(all_active_elems[keyboard_focus_tmp]);
		if (elem_tmp.is(':visible')) {
			keyboard_focus_new = keyboard_focus_tmp;
			if (elem_tmp.offset().top >= new_pos)
				break;
		}
	}
	if (keyboard_focus_new != keyboard_focus) {
		setFocusByIndex(keyboard_focus_new);
		return;
	}
	$(this).scrollToView();
}

//Command function: Move focus to the first focusable node
function focusFirst(/* uuid */) {
	var keyboard_focus_new = -1;
	while (++keyboard_focus_new <= all_active_elems.length - 1) {
		if ($(all_active_elems[keyboard_focus_new]).is(':visible')) {
			setFocusByIndex(keyboard_focus_new);
			return;
		}
	}
	$(this).scrollToView();
}

//Command function: Move focus to the last focusable node
function focusLast(/* uuid */) {
	var keyboard_focus_new = all_active_elems.length;
	while (--keyboard_focus_new >= 0) {
		if ($(all_active_elems[keyboard_focus_new]).is(':visible')) {
			setFocusByIndex(keyboard_focus_new);
			return;
		}
	}
	$(this).scrollToView();
}

//Command function: Edit requirement/test purpose description
function editDescription(/* uuid */) {
	if (!isEditable($(this)))
		return;
	if (countEditorInstances()>0) {
		for(var name in CKEDITOR.instances)
		{
			//TODO active
			var editor=CKEDITOR.instances[name];
			if(extractUUID(editor.element.$.id)==extractUUID(this.id) &&
				editor.config.bodyClass == 'normalCKE')
					return;
		}
		saveEditor();
	}
	if(keyboard_focus>-1)
		$($(all_active_elems[keyboard_focus]).find('.nodedesc')[0]).dblclick();
}

//Command function: Edit requirement name/test purpose ID (if description editor is opened
//it is closed with saving)
function editNameId(/* uuid */) {
	if (!isEditable($(this)))
		return;
	if (countEditorInstances()>0) {
		for(var name in CKEDITOR.instances)
		{
			var editor=CKEDITOR.instances[name];
			if(extractUUID(editor.element.$.id)==extractUUID(this.id) &&
				editor.config.bodyClass == 'miniCKE')
					return;
		}
		saveEditor();
	}
	if(keyboard_focus>-1)
		$($(all_active_elems[keyboard_focus]).find('.nodetitle')[0]).dblclick();
}


function countEditorInstances(){
	if(typeof CKEDITOR === 'undefined')
		return 0;
	var x = 0; 
	for(var instance in CKEDITOR.instances){ x++; }
	return x; 
}


//Command function: Save & close editor if requirement name/test purpose ID is being edited
function saveNameId(/* uuid */) {
	for(var name in CKEDITOR.instances){
		var editor = CKEDITOR.instances[name];
		if (editor && editor.element) {
			if (editor.config.bodyClass == 'miniCKE'){
				saveEditor();
				}
		}
	}
}

//Names of the Requality attribute responsible for storing node status
var statusAttr = {
	'Requirement': 'coverageStatus',
	'TextNode': '_textNodeType'
};

//List of possible statuses for different node types
var node_statuses = {
	'Requirement': {
		'in process': {
			icon: 'req-status-incomplete.png',
			className: 'status-normal'
		},
		'complete': {
			icon: 'req-status-complete.png',
			className: 'status-complete'
		},
		'verified': {
			icon: 'req-status-verified.png',
			className: 'status-verified'
		}
	},
	'TextNode':{

		'text': {
			icon: 'tntext.png',
			className: 'type-text'
		},

		'header': {
			icon: 'tnheader.png',
			className: 'type-header'
		}
	}
};


//Command function: Switch status of the requirement or test purpose //or text node
function changeStatus(uuid,type,status,silent) {
	var img = $('#status-' + uuid);
	var current_status = img.attr('alt');
	var new_status;
	var nodeType="";
	if(type)
		nodeType = type;
	else
		nodeType = getNodeType($(this));
	if(status)
		new_status=status;
	else
		if(nodeType == "TextNode"){
			if (current_status == 'text')
				new_status = 'header';
			else
				new_status = 'text';
			if (isReadOnly($(this)))
				return;
		}else{
			if (!isReadOnly($(this))) {
				// In normal mode 'verified' is unavailable
				if (current_status == 'in process')
					new_status = 'complete';
				else
					new_status = 'in process';
			}
			else {
				// In read-only (review) mode all statuses are allowed to be set
				if (current_status == 'in process')
					new_status = 'complete';
				else if (current_status == 'complete')
					new_status = 'verified';
				else
					new_status = 'in process';
			}
		}
	img.attr('alt', new_status).attr('title', new_status);
	img.attr('src', 'images/' + getNodeStatus(nodeType, new_status).icon);
	$(this).find('.nodetitle').removeClass(getNodeStatus(nodeType,current_status).className).addClass(getNodeStatus(nodeType,new_status).className);
	if(!silent)
		changeNodeAttr(uuid, statusAttr[nodeType], new_status);
}

//Command function: Delete the node after confirming the decision
function deleteNodeConfirm(uuid) {
	const fuuid = uuid;
	var thisa = this;
	loadNodeThen(uuid, function(){
		if (!canDeleteNode(extractUUID(thisa.id)) || !isEditable($(thisa))){
			alert("cannot delete this node")
			return;
		}
		removeNode(fuuid);
	});
}
function countChildren(nde){
	var par = getNodeByUUID(nde);
	var cnt = 0;
	for(var i = 0;i< par.children.length;i++){
		var chi = par.children[i];
		var name=getType(chi);
		if ( synonim[name] )
			name = synonim[name];
		if(!constructNodeWrapperProcessor[name])
			continue;
		var nodeChi = getNodeByUUID(chi);
		if(nodeChi)
			cnt++;
	}
	return cnt;//TODO check
}
function getChildNumber(nde){
	var node = getNodeByUUID(nde);
	var par = getNodeByUUID(node.parent);
	var cnt = 0;
	for(var i = 0;i< par.children.length;i++){
		var chi = par.children[i];
		var nodeChi = getNodeByUUID(chi);
		var isHeader = isNodeHeader(chi);
		if(nodeChi && isHeader)
			cnt++;
		if(chi == nde)
			if(isHeader)
				return cnt;
			else
				return 0;
	}
	return 0;//TODO check
}
function countChildrenOfType(id, type){
	var node = getNodeByUUID(id);
	var cnt = 0;
	if(node)
		for(var i = 0;i < node.children.length;i++){
			var chi = node.children[i];
			var nodeChi = getNodeByUUID(chi);
			if(nodeChi){
				var tp = getNodeTypeByNode(nodeChi);
				if(tp == type)
					cnt++;
			}
		}
	return cnt;//TODO check
}

//Command function: Add new child requirement (name is requested)
function addChildRequirementConfirm(uuid) {
	if ((window.readOnlyMode & EDIT_MODE_REQ_RO) != 0 || window.readOnly)
		return;      
	var node = getNodeByUUID(uuid);
	if(!node)
		return false;
	var type = getNodeTypeByNode(node);
	if(type == "TextNode")
		return;
	var parentUUID = getParentUUID(uuid);
	if (Number(countChildrenOfType(parentUUID, "TestPurpose"))>0)
		return;
	createNode(uuid,'Requirement',"Add Child Requirement[template]");
}

//Command function: Add new sibling requirement (name is requested)
function addSiblingRequirementConfirm(uuid) {
	if ((window.readOnlyMode & EDIT_MODE_REQ_RO) != 0 || window.readOnly)
		return;
	//var new_name = prompt('Requirement Id');
	var parentUUID = getParentUUID(uuid);
	var type = getNodeTypeByNode(getNodeByUUID(parentUUID));
	if(type != "Requirement")//TODO copy on textNode?
		return;
	if (Number(countChildrenOfType(parentUUID, "TestPurpose"))>0)
		return;
	createNode(parentUUID,'Requirement',"Add Sibling Requirement[template]",uuid);
}

function addChildTextNodeConfirm(uuid) {
	if ((window.readOnlyMode & EDIT_MODE_REQ_RO) != 0 || window.readOnly)
		return;
	if (Number(countChildrenOfType(uuid, "TestPurpose"))>0)
		return;
	createNode(uuid,'TextNode',"Add Child Text Node[template]");
}

//Command function: Add new sibling requirement (name is requested)
function addSiblingTextNodeConfirm(uuid) {
	if ((window.readOnlyMode & EDIT_MODE_REQ_RO) != 0 || window.readOnly)
		return;
	var parentUUID = getParentUUID(uuid);
	//var new_name = prompt('Requirement Id:                                  ');
	if (Number(countChildrenOfType(parentUUID, "TestPurpose"))>0)
		return;
	createNode(parentUUID,'TextNode',"Add Sibling Text Node[template]",uuid);
}


//Command function: Add new comment as requirement's or test purpose's child or another comment's sibling
function addComment(uuid) {
	// Get the wrapping block of the target requirement/test purpose
	// or of the target comment's parent requirement (if it is a comment)
	var req_elem = $(this).closest('.nodewrapper');
	// Create the child test purpose if there are no children requirements
	//if(id)
		   //nodeToFocus = id;
	createNode(extractUUID(req_elem[0].id),'Comment',"Add Comment[template]",uuid);
}

var internalClipboard;
var internalClipboardCopyMode;

function callAddTest(uuid){
	addTest(uuid);
}

function openTemplates(uuid){
	callOpenTemplates(uuid);
}
function canEditNode(uuid){//TODO readOnlyClasses
	var node = getNodeByUUID(uuid);
	if(node && node.attributes["_type"] && readOnlyClasses[node.attributes["_type"].value.toLowerCase()])
		return false;//TODO test
	return !getUpdatingState();
}

function editNode(uuid){
	if(canEditNode(uuid))
		callEditNode(uuid);
}
//Command function: Copy node to the internal clipboard (store the uuid)
function copyNodeToClipboard(uuid) {
	if (!isEditable($(this)))
		return;
	copyNode(uuid, false);
}

//Command function: Cut node to the internal clipboard (store the uuid)
function cutNodeToClipboard(uuid) {
	if (!isEditable($(this)))
		return;
	copyNode(uuid,true);
}

//Command function: Paste the copied node as the current node's sibling
function pasteNodeAsSibling(uuid) {
	if (!isEditable($(this)))
     return;
	var target_uuid = extractUUID($('#block-' + uuid).parent().closest('.nodewrapper')[0].id);
	return pasteNode(target_uuid, false, uuid);
}

//Command function: Paste the copied node with all its children as the current node's sibling
function pasteSubtreeAsSibling(uuid) {
	if (!isEditable($(this)))
     return;
	var target_uuid = extractUUID($('#block-' + uuid).parent().closest('.nodewrapper')[0].id);
 return pasteNode(target_uuid,true, uuid);
}

//Command function: Paste the copied node as the current node's child
function pasteNodeAsChild(uuid) {
	if (!isEditable($(this)))
     return;
 return pasteNode(uuid,false);
}

//Command function: Paste the copied node with all its children as the current node's child
function pasteSubtreeAsChild(uuid) {
	return pasteNode(uuid,true);
}

////////////////////////////////////////////////////////////////////////////////
//Startup actions

//Additional styles that should be applied into CKEditor when editing elements of specific class
var CKEditorStyles = {
	'reqname': 'body { margin: 1px; font-size: 125%; font-weight: bold; }'
};

//Processors for starting to drag different types of elements: determines the starting position
//for drag&drop highlighting and sets the appropriate classes to allow/disallow drop targets
//Input parameters:
//	dragging_uuid - UUID of the element being dragged
//Return value:
//	Initial value for the node indices to follow the mouse cursor
function dragRequirement(dragging_uuid, req_elems) {
	var underlying_req_idx = req_elems.index($('#node-' + dragging_uuid)) - 1;
	if (underlying_req_idx < 0) {
		underlying_req_idx = null;
	}
	// Disallow dragging onto itself and children
	$('#block-' + dragging_uuid + ' .requirement').addClass('undropable').addClass('disabled');
	// Make the whole node (including children) look disabled
	$('#block-' + dragging_uuid).addClass('disabled');
	
	
	return underlying_req_idx;
}
var draggingInitHandler = {
	'reqdesc': dragRequirement,
	'reqname': dragRequirement,
	'cmtdesc': function (dragging_uuid, req_elems) {
		var qnode = $('#node-' + dragging_uuid);
		var underlying_req_idx = req_elems.index(qnode) - 1;
     if (underlying_req_idx < 0) {
         underlying_req_idx = null;
     }
     // Disallow dragging onto itself
     qnode.addClass('undropable');
     // Make the whole node look disabled
     qnode.addClass('disabled');
     return underlying_req_idx;
 },
'tndesc': function (dragging_uuid, req_elems) {
	var qnode = $('#node-' + dragging_uuid);
	var underlying_req_idx = req_elems.index(qnode) - 1;
     if (underlying_req_idx < 0) {
         underlying_req_idx = null;
     }
     // Disallow dragging onto itself
     // Make the whole node look disabled
     qnode.addClass('undropable');
     qnode.addClass('disabled');
     return underlying_req_idx;
 }
};


function loadNodeTreeAndLoad(uuid, func){
	if(uuid in cache){
		func();
		return;
	}
	reactOnLoadTree.push(func);
	loadNodeSubtreeByUUID(uuid);
}

function getStartNode(){
  return startNode;
}

var dnd_overlay;
var isTempFuncCalled = false;
// Request templates for nodeTypes
function clearSelection(){
	var sel = window.getSelection ? window.getSelection() : document.selection;
	if (sel) {
    	if (sel.removeAllRanges) {
        	sel.removeAllRanges();
    	} else if (sel.empty) {
	        sel.empty();
    	}
	}
}
var dragActivated = false;
var dragActivateStarted = false;
var limYMin = null, limYMax = null, limXMin = null, limXMax = null;
var dragging = null;
var node = null;
var dims = null;
var dragging_init = null;
var dragging_uuid = null;
var scrollWatcher = null;
var mouseDX = null;
var mouseDY = null;
var reqHighlight = null;
var req_elems = null;
var underlying_req_idx1 = null;                // Indices of the requirement nodes between which the mouse cursor is
var underlying_req_idx2 = null;                // node1 is the higher node, node2 is the lower one (they can be equal)
function tfunc(){
	isTempFuncCalled = true;
	// On startup, request the nodes and create the HTML contents
	loadNodeTreeAndLoad(startNode, function(){
		try{
			addRootNode(startNode);
			//loadNodeSubtreeByUUID(startNode);
			var nodeData = getNodeByUUID(startNode);
			updateTree(startNode,0,nodeData.attributes._numericId? nodeData.attributes._numericId.newvalue:"");
			replaceDummies($(document));
			updateElList();
			setFocusByUUID(selectedNode, true);
			document.addEventListener('scroll', function (event) {
				$(".dummy").each(function(elem){
					var c = $(this);
					if(c.isInViewport()){
						replaceDummies(c.parent());
					}else
						return;
				});
			}, true /*Capture event*/);
			// When simplified editor is opened, update its internal styles
			// addCss() must be called before editor is loaded, so put it into 'instanceCreated'
			// Assign drag&drop action to the element titles (those allowed to be edited)
			// Mousedown is initial event for dragging, but real dragging actions will be performed
			// only on first moving, so that we could rename element by simply clicking it

			var dnd_keys = { ctrlKey: false, shiftKey: false };
			var dnd_keys_changed = 0;
			var dnd_keys_handled = 0;
			$(document).on('mousedown.drag','.dragable', function (e) {
				if(e.button!=0 || dragActivateStarted)
					return;
				if(!isEditable(node) || countEditorInstances()>0)
					return;
				dragActivateStarted = true;
				node = $(this).closest('.supported-node');
				dragging_init = this;
				dragging_uuid = extractUUID(dragging_init.id);
				dims = $(dragging_init).dimensions();
				var contDims = $(dragging_init).closest(".block").dimensions();
				limYMin = contDims.top;
				limYMax = contDims.bottom;
				limXMin = contDims.left;
				limXMax = contDims.right;
				mousePageX = e.pageX;
				mousePageY = e.pageY;
				mouseDX = e.pageX - dims.left;      // Remember position of the mouse relative to the dragged element
				mouseDY = e.pageY - dims.top;
				var scroll_speed = 0;                   // Speed of automatic scrolling
				var scroll_timer;                       // Timer for performing automatic scrolling
				var doc_height = $(document).height();  // Protect from heigth increasing when moving dragged element below bottom
				var wnd_height = $(window).height();
				var elem = this;
				// Data needed for requirement highlighting during drag&drop
				// They will be initialized later (to avoid delays in processing clicks without drag&drop)
				req_elems = null;                          // List of all requirement nodes available for highlightingvar underlying_req_idx1;                // Indices of the requirement nodes between which the mouse cursor is
				underlying_req_idx1 = null;                // Indices of the requirement nodes between which the mouse cursor is
				underlying_req_idx2 = null;                // node1 is the higher node, node2 is the lower one (they can be equal)		
		
				// Updates highlighting of the target requirement (under mouse cursor)
				// Input parameters:
				//	(mx, my) - mouse cursor position
				// Return value:
				//	none
				reqHighlight = function (mx, my) {
					if ((underlying_req_idx1 == null) || (underlying_req_idx2 == null))
						return;
		
					var elem1_current = $(req_elems[underlying_req_idx1]);
					var dims1_current = elem1_current.dimensions();
					var elem2_current = $(req_elems[underlying_req_idx2]);
					var dims2_current = elem2_current.dimensions();
					if (my < dims1_current.top) {
						// Above the node1
						elem1_current.removeClass('dropable');     // Unhighlight the node1 if it was highlighted
						underlying_req_idx2 = underlying_req_idx1;  // node1 becomes the new node2
						// Search for the new nodes where the mouse cursor is located now
						// (either on a node, or between two nodes)
						var tmp_idx1 = underlying_req_idx1;
						while (tmp_idx1 > 0) {
							--tmp_idx1;
							var elem_prev = $(req_elems[tmp_idx1]);
							if (elem_prev.is(':hidden')) {
								// Skip collapsed requirements
								continue;
							}
							underlying_req_idx1 = tmp_idx1;
							var dims_prev = elem_prev.dimensions();
							if (my > dims_prev.bottom) {
								// Cursor is between two nodes
								return;
							}
							else if ((my >= dims_prev.top) && (my <= dims_prev.bottom)) {
								// Cursor is vertically aligned with the node
								underlying_req_idx2 = underlying_req_idx1;
								if ((mx >= dims_prev.left) && (mx <= dims_prev.right)) {
									// Cursor is inside the node, set highlighting if necessary
									if (!elem_prev.hasClass('undropable') && !elem_prev.hasClass('droppable'))
										elem_prev.addClass('droppable');
								}
								return;
							}
							else /* if (my < dims_prev.top) */ {
								// Cursor is even higher, shift the indices and continue searching upwards
								underlying_req_idx2 = underlying_req_idx1;
							}
						}
						return;
					}
					else if ((my >= dims1_current.top) && (my <= dims1_current.bottom)) {
						// Aligned with the node1
						underlying_req_idx2 = underlying_req_idx1;
						if ((mx >= dims1_current.left) && (mx <= dims1_current.right)) {
							// Cursor is inside the node1, set highlighting if necessary
							if (!elem1_current.hasClass('undropable') && !elem1_current.hasClass('dropable'))
								elem1_current.addClass('dropable');
						}
						else {
							// node1 is not under the cursor - unhighlight it
							elem1_current.removeClass('dropable');
						}
						return;
					}
					else if ((my > dims1_current.bottom) && (my < dims2_current.top)) {
						// Between node1 and node2 (can happen only when the nodes are not the same)
						elem1_current.removeClass('dropable');
						elem2_current.removeClass('dropable');
					}
					else if ((my >= dims2_current.top) && (my <= dims2_current.bottom)) {
						// Aligned with the node2
						underlying_req_idx1 = underlying_req_idx2;
						if ((mx >= dims2_current.left) && (mx <= dims2_current.right)) {
							// Cursor is inside the node2, set highlighting if necessary
							if (!elem2_current.hasClass('undropable') && !elem2_current.hasClass('dropable'))
								elem2_current.addClass('dropable');
						}
						else {
							// node2 is not under the cursor - unhighlight it
							elem2_current.removeClass('dropable');
						}
						return;
					}
					else /* if (my > dims2_current.bottom) */ {
						// Below the node2
						elem2_current.removeClass('dropable');     // Unhighlight the node1 if it was highlighted
						underlying_req_idx1 = underlying_req_idx2;  // node2 becomes the new node1
						// Search for the new nodes where the mouse cursor is located now
						// (either on a node, or between two nodes)
						var tmp_idx2 = underlying_req_idx2;
						while (tmp_idx2 < req_elems.length - 1) {
							++tmp_idx2;
							var elem_next = $(req_elems[tmp_idx2]);
							if (elem_next.is(':hidden')) {
								// Skip collapsed requirements
								continue;
							}
							underlying_req_idx2 = tmp_idx2;
							var dims_next = elem_next.dimensions();
							if (my < dims_next.top) {
								// Cursor is between two nodes
								return;
							}
							else if ((my >= dims_next.top) && (my <= dims_next.bottom)) {
								// Cursor is vertically aligned with the node
								underlying_req_idx1 = underlying_req_idx2;
								if ((mx >= dims_next.left) && (mx <= dims_next.right)) {
									// Cursor is inside the node, set highlighting if necessary
									if (!elem_next.hasClass('undropable') && !elem_next.hasClass('dropable'))
										elem_next.addClass('dropable');
								}
								return;
							}
							else /* if (my > dims_next.bottom) */ {
								// Cursor is even lower, shift the indices and continue searching downwards
								underlying_req_idx1 = underlying_req_idx2;
							}
						}
						return;
					}
				};
		
				// Automatic scrolling handler
				scrollWatcher = function (e2) {
					if(!dragActivated)
					  return;
					if ((scroll_speed > 0) && ($(document).scrollTop() + wnd_height < doc_height) ||
						(scroll_speed < 0) && ($(document).scrollTop() > 0)) {
						// Perform scrolling only if not at the very top/bottom of the document
						//window.scrollBy(0, Math.round(scroll_speed / 90));
						// Calculate the updated position of the mouse (can do it since dragged element is 'display: fixed')
						var dragging_off = dragging.offset();
						// Update the target node highliting
						reqHighlight(e2.pageX, e2.pageY);
					}
				};
					
					
					//actions for links actions. Calls 'onaction' event
					//of element. Now used for links(linkto:).
			});
			$(document).on('mousemove', function (e) {
				if(!dragActivateStarted)
				  return;
				if(!dragActivated){
					if(e.pageX < limXMin || e.pageX > limXMax ||
						e.pageY < limYMin || e.pageY > limYMax ){
						dragActivated = true;
					}
					if(!dragActivated)
						return;
					e.preventDefault();
					if (dragging == null || !dragging) {
						// First mouse move after pressing the button - initialize dragging
						// Create a duplicate of the requirement name (it will be dragged
						// while the original one remains on its place)
						cachedDropTargets = null;
						dragging = $(node).clone()
						.removeClass('supported-node')
						.removeClass('dropTarget')
						.removeAttr('id').css({
							opacity: 0.7,
							position: 'fixed',
							left: dims.left + 'px',
							top: dims.top + 'px',
							zIndex: 1010,
							cursor: 'move',
							backgroundColor: '#f7f7ff',
							borderColor: '#c0c0c0'
						}).appendTo(document.body);
						// Initialize highlighting data and assign special styles to some nodes
						// to indicate impossibility of dropping the element there:
						// 'undropable' is set to requirement nodes to disallow highlighting;
						// 'disabled' is set to visualize unavailability of nodes by paleness
						//req_elems = $('.requirement');
						req_elems = $('.supported-node');
						req_elems.addClass('undropable');
						req_elems.addClass('disabled');
						req_elems = $('.dropTarget');
						for (var className in draggingInitHandler) {
							if ($(dragging_init).hasClass(className)) {
								underlying_req_idx2 = underlying_req_idx1 = draggingInitHandler[className].call(null, dragging_uuid, req_elems);
							}
						}
							// Listen to keypresses to handle Ctrl and Shift
						checkChilds(dragging_uuid, "", roots);
				 		scroll_timer = setInterval(scrollWatcher, 20);
					 }
				}
		
				mousePageX = e.pageX;
				mousePageY = e.pageY;
				if(!dragActivated)
				  return;
				dragging.offset({left: e.pageX-mouseDX, top: e.pageY-mouseDY});
				draggedNode = dragging_uuid;
				clearSelection();
				if(draggedNode){
					reqHighlight(e.pageX, e.pageY);
					if ($('#dnd-overlay').length == 0) {
						// If overlay is missing create it
						$('<div id="dnd-overlay"></div>').css({
							display: 'none',
							position: 'absolute',
							top: '0px',
							left: '0px',
							cursor: 'move',
							zIndex: 1000
						}).appendTo(document.body);
						// Update the size (in case document sizes changed) and display
						$('#dnd-overlay').css({
							width: $(document).width() + 'px',
							height: $(document).height() + 'px'
						}).show();
					}
	
					// Initialize autoscrolling watcher
	
					dnd_overlay = $('#dnd-cursor-overlay');
	
				   if (dnd_overlay.length == 0) {
						// Create the "plus-sheet" overlay to indicate copying
						dnd_overlay = $('<img src="images/dnd-copy.png" alt="" id="dnd-cursor-overlay" />').css({
							position: 'absolute',
							display: 'none',
							opacity: 0.6,
							zIndex: 1020
						}).appendTo(document.body);
					}

					// Update the position of the dragged element and drag&drop overlay (if it's visible)
					if (dnd_overlay.is(':visible')) {
						var dragging_dims = dragging.dimensions();
						dnd_overlay.offset({
							top: dragging_dims.bottom - 10,
							left: dragging_dims.right - 10
						});
					}
				}

			});
			
			$(document).bind('keydown.drag keyup.drag', function (e) {
				if (e.type == 'keydown') {
					if (e.keyCode == 17) {
						// Ctrl is pressed
						if (!dnd_keys.ctrlKey) {
							dnd_keys.ctrlKey = true;
							dnd_keys_changed = dnd_keys_changed | 1;
						}
					}
					else if (e.keyCode == 16) {
						// Shift is pressed
						if (!dnd_keys.shiftKey) {
							dnd_keys.shiftKey = true;
							dnd_keys_changed = dnd_keys_changed | 2;
						}
					}
				}
				else if (e.type == 'keyup') {
					if (e.keyCode == 17) {
						// Ctrl is released
						if (dnd_keys.ctrlKey) {
							dnd_keys.ctrlKey = false;
							dnd_keys_changed = dnd_keys_changed & 2;
						}
					}
					else if (e.keyCode == 16) {
						// Shift is released
						if (dnd_keys.shiftKey) {
							dnd_keys.shiftKey = false;
							dnd_keys_changed = dnd_keys_changed & 1;
						}
					}
				}
				if(!dragActivated)
					return;
				// Highlight the requirement the mouse is passing by

				dnd_overlay = $('#dnd-cursor-overlay');
				
				if (dnd_overlay.length > 0 && (dnd_keys_changed != dnd_keys_handled || dnd_keys_handled == 0)) {
					reqHighlight(e.pageX, e.pageY);
					dnd_keys_handled = dnd_keys_changed; 
					// Drag&Drop type changed, updating the overlay
					dnd_overlay.show();
					if (dnd_keys.ctrlKey) {
						// Ctrl or Ctrl+Shift is pressed, update the overlay and show it
						if (dnd_keys.shiftKey)
							dnd_overlay.attr('src', 'images/dnd-copy-all.png');
						else
							dnd_overlay.attr('src', 'images/dnd-copy.png');
						var dragging_dims = dragging.dimensions();
						dnd_overlay.show().offset({
							top: dragging_dims.bottom - 10,
							left: dragging_dims.right - 10
						});
					}
					else {
						// Ctrl is not pressed, hide the overlay
						dnd_overlay.hide();
					}
				}
				// Calculate auto-scrolling speed (non-zero when mouse is at the top/bottom of the window)
				var scrollTop = $(document).scrollTop();
				if (e.pageY - scrollTop < 30) {
					// Top of the window, scroll up (negative speed)
					// Max speed is 1800 px/sec
					var diff = e.pageY - scrollTop;
					if (diff < 0)
						diff = 0;
					scroll_speed = -60 * (30 - diff);
				}
				else if (scrollTop + $(window).height() - e.pageY < 30) {
					// Bottom of the window, scroll down (positive speed)
					var diff = scrollTop + $(window).height() - e.pageY;
					if (diff < 0)
						diff = 0;
					scroll_speed = 60 * (30 - diff);
				}
				else {
					scroll_speed = 0;
				}
			});
			
			$(document).on('mouseup.drag', function (e) {
				dragActivated = false;
				dragActivateStarted = false;
				limYMin = null, limYMax = null, limXMin = null, limXMax = null;
				node = null;
				dims = null;
				dragging_init = null;
				underlying_req_idx1 = underlying_req_idx2 = req_elems = null; 
				scrollWatcher = null
				mouseDX = null;
				mouseDY = null;
				reqHighlight = null;
				// The mouse button is released: either normal click, or end of drag&drop
				e.preventDefault();
				draggedNode = null;
				// In any case unbind the additional event handlers
				$('.undropable').removeClass('undropable');
				$('.disabled').removeClass('disabled');
				cachedDropTargets = null;
				if (dragging!=null) {
					// Drag&drop was used - restore normal functioning by removing the dragged element,
					// hiding the overlays, unhighlighting the requirement and turning autoscroll off
					scroll_speed = 0;
					clearInterval(scroll_timer);
					dragging.remove();
					dragging = null;
					if(dnd_overlay){
						dnd_overlay.remove();
						dnd_overlay = null;
					}
					var target = $('.dropable').removeClass('dropable');
					$('#dnd-overlay').hide();
					if (target.length > 0) {
						// If we released the element over a node, complete the drag&drop action
						var target_uuid = extractUUID(target[0].id);
						//setFocus(target);
						if (dnd_keys.ctrlKey) {
							// Ctrl was pressed, so making a copy instead of moving
							if (dnd_keys.shiftKey) {
								// Ctrl+Shift was pressed, make a copy of the subtree
								copyPasteNode(dragging_uuid, target_uuid, true, true);
							}
							else {
								// Only Ctrl was pressed, make a copy of the element itself
								copyPasteNode(dragging_uuid, target_uuid, true, false);
							}
						}
						else {
							// No keyboard modifiers were pressed, move the element
							changeNodeParent(dragging_uuid, target_uuid,e.shiftKey);
						}
					}
					dragging_uuid = null;
				}
			});
			// Make editable elements replaced with editor by clicking on them
			$(document).on('dblclick','.reditable', function (e) {
				var elem = document.getElementById(this.id);
				if(editorJavaDialog)
					return;
				if(this.id==activeEditorElem)
				  return;//current element
				e.stopPropagation();
				e.stopImmediatePropagation();
				e.preventDefault();
				if(editorJavaDialog)
					return;
				var el = $(elem).closest('.supported-node');
				if (!(el.hasClass('focused')))
					setFocus(elem);
				//if(countEditorInstances()<1)
				putEditor(elem, false);
			});
			// Make renamable elements updated by clicking on them
			$(document).on('dblclick','.createText', function (e) {
				var elem = this;
				if(editorJavaDialog)
					return;
				if(this.id==activeEditorElem)
				  return;//current element
				  
				e.stopPropagation();
				e.stopImmediatePropagation();
				e.preventDefault();
				
				putEditor(elem, false);
				$(elem).css("height","1.2em");
				$(elem).css("background-color","#ffefff");
				$(elem).on("mouseleave",function(e){
					$(elem).css("height","0.14em");
					$(elem).css("background-color","#f0f0f0");
					
				});
				//if(countEditorInstances()<1){
				//}
			});
		
			$(document).on('dblclick','.renamable', function (e) {
				var elem = document.getElementById(this.id); 
				if(editorJavaDialog)
					return;
				var el = $(elem).closest('.supported-node');
				if(this.id==activeEditorElem)
				  return;//current element 
				e.preventDefault();
				e.stopImmediatePropagation();
				e.stopPropagation();
				if (!(el.hasClass('focused')))
					setFocus(elem);
				//if(countEditorInstances()<1)
				putEditorWithHtmlClean(elem, true, true);
			});
			$(document).on('click','.supported-node', function (e) {
				if(editorJavaDialog)
					return;
				if ($(this).hasClass('focused'))
					return;
		//		e.stopPropagation();
		//		e.stopImmediatePropagation();
		//		e.preventDefault();
				setFocus($(this));
			});
			// Expand menu for requirements and test purposes on hovering
			$(document).on('mouseenter','.nodectrls', function () {
				var menu_caller = $(this);
				var mn = menu_caller.children(".menu");
				var dims_provider = menu_caller;
				if(mn.length>0){
					var bl = menu_caller.closest(".supported-node");
					if(bl.length>0){
						var uuid = extractUUID(bl.attr("id"));
						window.MENU_HOTKEYS = uuid;
						const fuuid = uuid;
						loadNodeThen(uuid, function(){
							mn.html(constructHotMenu(fuuid));
							mn.hide();	
						});
					}
				}else
				  return;
				var menu_caller_dims = dims_provider.dimensions();
				var container;
				// If we expand the existing element it will stretch the table cell with
				// the test purpose ID or requirement name. To avoid this we create a copy of the
				// drop-down menu, position it as "absolute" above the old one and expand.
				//TODO replace by mn??
				var new_menu = mn.clone().hide();
				$(".nodectrls-virtual").mouseleave();
				new_menu.find('.menu-item,.menu-item-disabled').each(function(e){
					var name = $(this).find(".menu-name");
					if(name && name.text().indexOf("[template")>0){
						var text = name.text();
						var ind = text.indexOf("[template");
						var fullitem = text.substr(ind, text.indexOf("]", ind)-ind+1);
						var type = text.substr(ind+10,text.indexOf("]", ind)-ind-10);
						var uuid = extractUUID(new_menu.children(".menu")[0].id);
						//printToConsole(uuid);
						var label = getActiveTemplateLabel(uuid,type);
						if(!label)
							label = "";
						else 
							label = " - "+label;
						name.text(name.text().replace(fullitem, label));
					}
				});
				// Change class so that the new menu did not react to 'mouseenter' second time
				new_menu.removeClass('nodectrls').addClass('nodectrls-virtual');
				if (menu_caller.hasClass('reqctrls')) {
					// The new menu is hovered, so the border around the current item disappears.
					// Set new class to keep the border.
					// Remove node-specific styles
					new_menu.removeClass('reqctrls');
				}
				else if (menu_caller.hasClass('cmtctrls')) {
					new_menu.removeClass('cmtctrls');
				}
				container = menu_caller.closest('.supported-node');
				container.addClass('supported-node-hovered');
				// Calculate how much the menu should be stretched.
				// jQuery Actual Plugin is used for getting size of a hidden element.
				var delta_width = menu_caller.find('.menu').actual('outerWidth') - menu_caller_dims.width + 15;
				var delta_height = menu_caller.find('.menu').actual('outerHeight');
				// Set mousemove listener to manually watch for mouse cursor going out of the menu
				// (mouseleave for unknown reasons is not always triggered, and menu remains on screen)
				var inside_menu = true;
				var moveFunction = function (e) {
					var menu_sz = new_menu.dimensions();
					if ((e.pageX < menu_sz.left) || (e.pageX > menu_sz.right) || (e.pageY < menu_sz.top) || (e.pageY > menu_sz.bottom)) {
						new_menu.mouseleave();
					}
				};
				new_menu.bind('mousemove', moveFunction);
				// Chaining:
				// 1) setting initial position and size set to the original menu's;
				// 2) inserting the element into document;
				// 3) displaying the internal menu contents;
				// 4) animate expanding the menu;
				// 5) assigning menu collapse to moving mouse out of the menu.
				new_menu.css({
					position: 'absolute',
					top: menu_caller_dims.top + 'px',
					left: menu_caller_dims.left + 'px',
					width: menu_caller.width() + 'px',
					height: menu_caller.height() + 'px',
					zIndex: 1000
				}).appendTo(container).show().find('.menu').show().end()./*stop(true, false).*/animate({
					width: '+=' + delta_width,
					left: '-=' + delta_width,
					height: '+=' + delta_height
				}, animationPeriod).mouseleave(function () {
					// Protect from double-entering
					if (inside_menu) {
						inside_menu = false;
						// Unbind mousemove listener
						$(document).unbind('mousemove.menu');
						// When mouse goes away restore normal classes for "item-under-cursor" highlighting
						container.removeClass('supported-node-hovered');
						// ...and animate collapsing the menu
						
						$(this)./*stop(true, false).*/animate({
							width: '-=' + delta_width,
							left: '+=' + delta_width,
							height: '-=' + delta_height
						}, animationPeriod, function () {
							// Collapsing is finished - remove the absolute-position menu-duplicate
							$(this).remove();
						});
					}
				});
		    });
			// Hotkey handlers
			$(document).keydown(function (e) {
				if(!window.MENU_HOTKEYS&&keyboard_focus==-1)
                  return;
				var sel = null;
				var uuid = null;
				if(window.MENU_HOTKEYS == "focus" || !window.MENU_HOTKEYS){
				  sel = all_active_elems[keyboard_focus];
				  uuid = extractUUID(sel.id);
				}else{
				  sel = $("#node-" + window.MENU_HOTKEYS);
				  if(sel.length>0)
				    sel = sel[0];
				  uuid = window.MENU_HOTKEYS;
				}
				//var elem = all_active_elems[keyboard_focus];
				hotkeyManager.execHotkeyAction(e, getHotKeyMode(), sel, uuid);
			});
				
		}catch(t){
			alert(t.stack);
			alert(t.name+" "+t.message+" "+t.stack);
		}
	
	});
}
templateLoadedActions = tfunc;

function initTempContent(){
	editorTempContent = document.createElement('div');
	editorTempContent.className = "hiddenPoint";	
	$(editorTempContent).appendTo(document.body);
}

var miniConfig = {
			bodyClass: 'miniCKE',
			autoUpdateElement:false,
			disableObjectResizing:true,
			language: 'en',
			autoParagraph: false,
			skin: 'moono-lisa',
			startupShowBorders:false,
			removePlugins: 'toolbar',
			toolbar: "empty",
			toolbarStartupExpanded:false,
			toolbarCanCollapse: true,
			resize_enabled: false,
	        forcePasteAsPlainText: false,
			extraPlugins: 'requality'
		};

var normalConfig = {
			bodyClass: 'normalCKE',
			autoUpdateElement:false,
			disableObjectResizing:true,
			language:'en',
			autoParagraph:false,
			skin: 'moono-lisa',//??
			startupShowBorders:true,
			removePlugins: '',
			toolbar:"Requality",
			toolbarStartupExpanded:true,
			toolbarCanCollapse:false,
			resize_enabled: true,
	        forcePasteAsPlainText: false,
			extraPlugins:'toolbar,SimpleImage,sourcedialog,requality'
		};
var conf;
var menuHidder = function (ev){
  ev.removeListener();
  document.getElementById(ev.editor.id + '_top').style.display = 'none';  
  ev.editor.on('focus', function (e) {  
     document.getElementById(ev.editor.id + '_top').style.display = 'block';  
  });  
  ev.editor.on('blur', function (e) {  
     document.getElementById(ev.editor.id + '_top').style.display = 'none';  

  });  
}
var docLoadActions = function() {
	
	if(window.templateLoaded && !isTempFuncCalled)
		templateLoadedActions();
	CKEDITOR.disableAutoInline = true;
	CKEDITOR.config.toolbar_empty = [];
	
//     var s = document.createElement("script");
//     s.type = "text/javascript";
//     s.language = "javascript";
//     s.src = "firebug-lite.js";
//     $("head").append(s);
   $(document).on('mousedown','.clickable', function (event) {
	    	event.stopPropagation();
	        event.stopImmediatePropagation();
	        event.preventDefault();
	        saveEditor();
	    });
   window.inited = true;
   $(document).ready(function() {
    	$('.toolsPanel').scrollToFixed();
   });
   CKEDITOR.disableAutoInline = true;
};
if(document.readyState == "loading")
   window.addEventListener('DOMContentLoaded',  docLoadActions	);
else
   docLoadActions();
//]]>
