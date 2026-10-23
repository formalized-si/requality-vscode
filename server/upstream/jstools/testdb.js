// Add new constant to support read-only mode for test purposes
const EDIT_MODE_TP_RO  = 2;
const EDIT_MODE_TSTEP_RO  = 4;
const EDIT_MODE_TPROC_RO  = 32;

var indexes = [];
function formTPDesc(uuid){
	var nodeData = getNodeByUUID(uuid);
	if (nodeData.attributes._expectedResults && nodeData.attributes._expectedResults.value){
		//expectedResults = '<br />-- Expected results:<br />' + nodeData.attributes._expectedResults.value.replace(/  /g,"&nbsp;&nbsp;");
		if(!nodeData.attributes._ldescription)
			nodeData.attributes._ldescription = {"value":"","newvalue":""};
		return{"value":
			nodeData.attributes._ldescription.value+'<br />-- Expected results:<br />' + nodeData.attributes._expectedResults.value.replace(/  /g,"&nbsp;&nbsp;"),
				"newvalue":
					nodeData.attributes._ldescription.newvalue+'<br />-- Expected results:<br />' + nodeData.attributes._expectedResults.newvalue.replace(/  /g,"&nbsp;&nbsp;")};
	}
	return {"value":null,"newvalue":null};
}
// Command function: Add new test purpose as requirement's child or other testpurpose's sibling
function addTestPurpose(uuid) {
	if ((readOnlyMode & EDIT_MODE_TP_RO) != 0)
		return;
	// Get the wrapping block of the target requirement (if it is a requirement)
	// or of the target test purpose's parent requirement (if it is a test purpose)
	var req_elem = $(this).closest('blockquote.indent');
	// Create the child test purpose if there are no children requirements
	if (!req_elem.hasClass('haschildreq')) {
		createNode(extractUUID(req_elem[0].id),'TestPurpose',"Add Test Purpose[template]");
	}
}


function addTestStepBefore( uuid ) 
{
    if( (readOnlyMode & EDIT_MODE_TSTEP_RO) != 0 ) return;
    createTestStep( getStartNode(), getStartNode() );
//	if(uid)
//		nodeToFocus = uid;
}

function addTestStepAfter( uuid ) 
{
    if( (readOnlyMode & EDIT_MODE_TSTEP_RO) != 0 ) return;
    createTestStep( getStartNode(), uuid );
//    if(uid)
//		nodeToFocus = uid;
}

function addCopyTestStepAfter( uuid ) 
{
	if( (readOnlyMode & EDIT_MODE_TSTEP_RO) != 0 ) return;
	copyNode(uuid, false);
	pasteNode(getNodeByUUID(uuid).parent, false, uuid);
    //createTestStep( getStartNode(), uuid );
}

function testStepUpdate( uuid ){
	var elems = $('.linked');
		for (var i = 0; i < elems.length; i++){
			if(extractUUID(elems[i].id) == uuid)
				elems[i].innerHTML = getUFName(uuid);
		}
}

// Command function: Switch test step type
function changeTestStepType(uuid) {
    var tstype = $('#tstype-' + uuid);
    var currentTSType = tstype.text();
    var newTSType;
    if( currentTSType == 'act' )    newTSType = 'verify'; else
    if( currentTSType == 'verify' ) newTSType = 'ensure'; else
    if( currentTSType == 'ensure' ) newTSType = 'act';
    
    var nodeType = getNodeType($(this));

    tstype.text( newTSType );
    
    changeNodeAttr(uuid, "_tstype", newTSType );
    //updateTree(uuid);
}

function unlinkTestPurpose(uuid) {
	var nodeData = getNodeByUUID(uuid);
	//call removelink 
	removeTestPurposeLink(uuid);
	//updateTree(uuid);events tends to be from attr change
}

function linkTestPurpose(uuid) {
	//call java method that pop up dialog and
	//add * link if necessary
	addSingleTestPurposeToTestStep(uuid);
	//updateTree(uuid);event tends to be from attr change
}
function getBetterPositionLabel(pos){
   var intpos = parseInt(pos);
   intpos++;
   if(intpos<10)
     return "0"+intpos;
   return intpos;
}
function getTPMode(){
	if(window.testProcedureMode)
		return window.testProcedureMode;
 	var node = getNodeByUUID(startNode);
    var startNodeType = node.attributes._type.value;
    window.testProcedureMode = (startNodeType == "TestProcedure");
	return window.testProcedureMode;
}
function cntrlPAction(uuid){
	return (getTPMode()?addTestStepAfter:addTestPurpose)(uuid);
}
function specifyInitialConditions(uuid)
{
//    saveEditor();
//    $(".tprocinitcondwrapper").css("display","block");
    putEditor($(".tprocinitcond").get(0),false);
}

		// Register new hotkey for adding test purposes
	hotkeyManager.registerHotkeys({
		'Ctrl+P': { handlerGlobal: cntrlPAction, handlerCKEditor: null }
	});
	hotkeyManager.registerHotkeys({
		'P':         	  	{ handlerGlobal: null,		             handlerCKEditor: null, 			handlerHotMenu: addTestPurpose}
	});
	
	
	// Implement Save actions for test purpose specific fields
	saveAction['tpid'] = function (uuid, new_data) {
		new_data = new_data.unescapeHtml();
		if(new_data == "")
			new_data = null;
		if(new_data != null)
			new_data = new_data.replace(/&nbsp;/g, " ");
		if(checkName(uuid,new_data))
			changeNodeAttr(uuid, '_name', new_data);
		else{
			var name = getName(getNodeByUUID(uuid));
			window.attributeUpdate['_name'](uuid,"_name",name,name);
		}
	};
	saveAction['tpdesc'] = function (uuid, new_data) {
		// Split to Description and Expected Results
		var desc;
		var exp_res;
		var pos = new_data.indexOf('-- Expected results:');
		if (pos >= 0) {
			desc = new_data.substr(0, pos).replace(/<br\s*\/>\s*$/, '');
			exp_res = new_data.substr(pos + 20).replace(/^\s*<br\s*\/>/, '');
			// Collect all tags that remain opened at the "Expected results" delimiter
			var tag_stack = [];
			pos = 0;
			while ((pos = desc.indexOf('<', pos)) != -1) {
				// Tag started...
				var pos_end = desc.indexOf('>', pos);
				if (pos_end == -1)
					break;   // Corrupted XHTML, stop parsing
				if (desc[pos + 1] == '/') {
					// This is a closing tag - remove from the stack
					var tag_name = desc.substring(pos + 2, pos_end);
					var last_tag = tag_stack.pop();
					if (tag_name != last_tag.replace(/ .*$/, '')) {
						tag_stack.push(last_tag);
					}
				}
				else {
					if (desc[pos_end - 1] != '/') {
						// This is an opening and not self-closing tag - push to the stack
						tag_stack.push(desc.substring(pos + 1, pos_end));
					}
				}
				pos = pos_end;
			}
			// Close all opened tags in the end of first part (Description)
			// and duplicate them at the beginning of the second part (Expected results)
			var exp_res_prefix = '';
			var last_tag;
			while (last_tag = tag_stack.pop()) {
				desc += '</' + last_tag.replace(/ .*$/, '') + '>';
				exp_res_prefix = '<' + last_tag + '>' + exp_res_prefix;
			}
			exp_res = exp_res_prefix + exp_res;
		}
		else {
			desc = new_data;
			exp_res = '';
		}
		changeNodeAttr(uuid, '_description', desc.replace(/&nbsp;/g, " "));
		changeNodeAttr(uuid, '_expectedResults', exp_res.replace(/&nbsp;/g, " "));
	};
    saveAction['tprocid'] = function (uuid, new_data) {
    	new_data = new_data.unescapeHtml();
		if(new_data == "")
			new_data = null;
		if(new_data != null)
			new_data = new_data.replace(/&nbsp;/g, " ");
		if(checkName(uuid,new_data))
			changeNodeAttr(uuid, '_name', new_data);
		else{
			var name = getName(getNodeByUUID(uuid));
			window.attributeUpdate['_name'](uuid,"_name",name,name);
		}
    };
    saveAction['tprocdesc'] = function (uuid, new_data) {
        changeNodeAttr(uuid, '_description', new_data.replace(/&nbsp;/g, " "));
    }
    saveAction['tprocinitcond'] = function (uuid, new_data) {
        changeNodeAttr(uuid, '_initcond', new_data.replace(/&nbsp;/g, " "));
    }
    saveAction['tsdesc'] = function (uuid, new_data) {
        changeNodeAttr(uuid, '_description', new_data.replace(/&nbsp;/g, " "));
    }

	// Add read-only class binding
	readOnlyClasses[EDIT_MODE_TP_RO] = 'testpurpose';
    readOnlyClasses[EDIT_MODE_TPROC_RO] = 'testprocedure';
    readOnlyClasses[EDIT_MODE_TSTEP_RO] = 'teststep';

	// Append command to Requirement nodes for creating test purpose children
	node_commands['Requirement']['addtp'] = {
		menu: true,
		name: 'Add new Test Purpose',
		createdType: 'TestPurpose',
		image: 'add-tp.png',
		imageAlt: '[+]',
		readOnlyMode: EDIT_MODE_TP_RO|EDIT_MODE_VIRTUAL,
		handler: addTestPurpose
	};
	// Add drop-down menu to test purpose nodes
	node_commands['TestPurpose'] = {
		'deltp': {
			menu: true,
			name: 'Delete Test Purpose',
			image: 'delete-node.png',
			imageAlt: '[&minus;]',
			readOnlyMode: EDIT_MODE_TP_RO,
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

		'addTest': {
			menu: true,
			name: 'Add Test',
			image: 'dnd-copy.png',
			imageAlt: '[T]',
			readOnlyMode: EDIT_MODE_VIRTUAL,
			handler: callAddTest
		},
		'addtp': {
			menu: true,
			name: 'Add new Test Purpose',
			createdType: 'TestPurpose',
			image: 'add-tp.png',
			imageAlt: '[+]',
			readOnlyMode: EDIT_MODE_TP_RO|EDIT_MODE_VIRTUAL,
			handler: addTestPurpose
		},
		'addcmt': {
			menu: true,
			name: 'Add new Comment',
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
			handler: changeStatus
		}
	};
    // Add drop-down menu to test procedure nodes
    node_commands['TestProcedure'] = {
        'addts': {
            menu: true,
            name: 'Add new Test Step',
            createdType: 'TestStep',
            image: 'add-ts.png',
            imageAlt: '[+]',
            readOnlyMode: EDIT_MODE_TSTEP_RO|EDIT_MODE_VIRTUAL|EDIT_MODE_TPROC_RO,
            handler: addTestStepBefore
        },
		'editNode':{
			menu: true,
			name: 'Open Editor',
			image: 'req-status-complete.png',
			imageAlt: '[E]',
			readOnlyMode: EDIT_MODE_TPROC_RO,
			handler: editNode
		},
//        'specifyinitconds': {
//            menu: true,
//            name: 'Specify initial conditions',
//            image: 'specify-initconds.png',
//            imageAlt: '[+]',
//            readOnlyMode: EDIT_MODE_TSTEP_RO,
//            handler: specifyInitialConditions
//        },
        'chstatus': {
            menu: true,
            image: 'status.png',
			name: 'Change status',
			imageAlt: '[S]',
			readOnlyMode: 0,
            handler: changeStatus
        }
    };
    // Add drop-down menu to test procedure steps
    node_commands['TestStep'] = {
        'delts': {
            menu: true,
            name: 'Delete Test Step',
            image: 'delete-node.png',
            imageAlt: '[&minus;]',
            readOnlyMode: EDIT_MODE_TSTEP_RO,
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
        'addts': {
            menu: true,
            name: 'Add new Test Step',
			createdType: 'TestStep',
            image: 'add-ts.png',
            imageAlt: '[+]',
            readOnlyMode: EDIT_MODE_TSTEP_RO|EDIT_MODE_VIRTUAL,
            handler: addTestStepAfter
        },
		'addtssib': {
			menu: true,
			name: 'Clone Test Step',
			image: 'add-ts.png',
			imageAlt: '[+]',
			readOnlyMode: EDIT_MODE_TSTEP_RO|EDIT_MODE_VIRTUAL,
			handler: addCopyTestStepAfter
		},
        'linktp': {
            menu: true,
            name: 'Link Test Purpose',
            image: 'link-tp.png',
            imageAlt: '[+]',
            readOnlyMode: EDIT_MODE_TSTEP_RO,
            handler: linkTestPurpose
        },
        'unlinktp': {
            menu: true,
            name: 'Unlink Test Purpose',
            image: 'unlink-tp.png',
            imageAlt: '[+]',
            readOnlyMode: EDIT_MODE_TSTEP_RO,
            handler: unlinkTestPurpose
        },
        'openlink': {
            menu: false,
            name: 'Open editor for node',
            readOnlyMode: 0,
            handler: openEditorForNode
        },
        'chtstype': {
            menu: true,
			image: 'status.png',
			imageAlt: '[S]',
            name: 'Change Test Step type',
            readOnlyMode: 0,
            handler: changeTestStepType
        }
    };

	// Fill in list of statuses
	node_statuses['TestPurpose'] = {
		'in process': {
			icon: 'tp-status-incomplete.png',
			className: 'status-normal'
		},
		'complete': {
			icon: 'tp-status-complete.png',
			className: 'status-complete'
		},
		'verified': {
			icon: 'tp-status-verified.png',
			className: 'status-verified'
		}
	};
    node_statuses['TestProcedure'] = {
        'in process': {
            icon: 'tproc-status-incomplete.png',
            className: 'status-normal'
        },
        'complete': {
            icon: 'tproc-status-complete.png',
            className: 'status-complete'
        },
        'verified': {
            icon: 'tproc-status-verified.png',
            className: 'status-verified'
        }
    };
    node_statuses['TestStep'] = {
        'in process': {
            icon: 'ts-status-incomplete.png',
            className: 'status-normal'
        },
        'complete': {
            icon: 'ts-status-complete.png',
            className: 'status-complete'
        },
        'verified': {
            icon: 'ts-status-verified.png',
            className: 'status-verified'
        }
    };

	// Insert TestPurpose node type into the order list at the last but one position (just before Requirement)
	childrenNodesOrder.splice(-1, 0, 'TestPurpose','TestProcedure','TestStep');
	// Add test purpose node constructors
	constructNodeWrapperProcessor['TestPurpose'] = function (uuid) {
		return $('<div class="children-TestPurpose" id="children-TestPurpose-'+uuid+'"><div class="testpurpose-title">Test Purposes:</div></div>');
	};

    window.constructNodeProcessor['TestPurpose'] = function (nodeData, uuid) {
    	var tsindex = 0;
		// Fill in default values for missing attributes
		if (!nodeData.attributes._description || !nodeData.attributes._description.value)
			nodeData.attributes._description = { 'value': '&nbsp;' };
		if (!nodeData.attributes._status || !nodeData.attributes._status.value)
			nodeData.attributes._status = { 'value': 'in process' };
		nodeData.attributes._description.value = nodeData.attributes._description.value.replace(/  /g,"&nbsp;&nbsp;");
		//nodeData.id = nodeData.id;
		var nid = getNameOrId(nodeData);
		nodeData.attributes._nameOrId=getNameOrId(nodeData);
		// Combine description and expected results together
		//var expectedResults = '';
		nodeData.attributes._ldescription=nodeData.attributes._description;
		nodeData.attributes._description = formTPDesc(uuid);
		nodeData.attributes._vdescription=getDescription(uuid);
		// Construct HTML block
		var editable_prefix = (((readOnlyMode & EDIT_MODE_TP_RO) == 0) ? '' : 'non');
		var newNode = $(
			'<div class="tpwrapper nodewrapper" id="block-' + uuid + '">' +
			  '<div style="height: 1px; width: 100%; border-top: solid 1px gray;"></div>' +
			  '<div class="testpurpose supported-node" id="node-' + uuid + '">' +
			    '<table cellpadding="0" cellspacing="0" border="0" width="100%" class="tpid_wrap">' +
			      '<tr>' +
			        '<td>' +
			          '<img src="images/' + getNodeStatus('TestPurpose',nodeData.attributes._status.value).icon + '" alt="' + nodeData.attributes._status.value + '" title="' + nodeData.attributes._status.value + '" id="status-' + uuid + '" class="imgstatus" onclick="javascript:execNodeCommand(\'TestPurpose\', \'' + uuid + '\', \'chstatus\');" />' +
			        '</td>' +
			        '<td width="100%">' +

			          '<div class="hiddenPoint" id="' + uuid + '-attribute-_nameOrId-_raw">' +
			          	nid.value +
			          '</div>' +
			          '<div class="tpid nodetitle ' + editable_prefix + 'renamable ' + getNodeStatus('TestPurpose',nodeData.attributes._status.value).className + ' dragable tpid-' + uuid + '" id="' + uuid + '-attribute-_nameOrId" contenteditable="false">' +
			          	nid.newvalue +
			          '</div>' +
			        '</td>' +
			        '<td>' +
			          '<div class="tpctrls nodectrls">' +
			            '<div style="text-align: right;">' +
			              '<img src="images/menu.png" alt="[v]" />' +
			            '</div>' +
			            '<div class="menu" id="menu-'+uuid+'" style="display: none;">' +
			              constructMenu('TestPurpose', uuid) +
			            '</div>' +
			          '</div>' +
			        '</td>' +
			      '</tr>' +
			    '</table>' +
			    '<div class="hiddenPoint tpdesc-' + uuid + '-_raw" id="' + uuid + '-attribute-_vdescription-_raw" contenteditable="false">' +
			      nodeData.attributes._vdescription.value +
			    '</div>' +
			    '<div class="tpdesc nodedesc ' + editable_prefix + 'reditable tpdesc-' + uuid + '" id="' + uuid + '-attribute-_vdescription" contenteditable="false">' +
			      nodeData.attributes._vdescription.newvalue +
			    '</div>' +
			  '</div>' +
			'</div>'
		);

		// Enumerate all children objects and recursively construct the rest of the tree
//		var childrenNodes = {};
//		for (var i in nodeData.children) {
//			// Construct the next child subtree (for requirements) or table (for test purposes)
//			var chldNode = constructTree(nodeData.children[i]);
//			var chldType = chldNode.type;
//			if (!childrenNodes[chldType]) {
//				var wrapperConstructor = constructNodeWrapperProcessor[chldType];
//				if (wrapperConstructor)
//					childrenNodes[chldType] = wrapperConstructor(uuid);
//				else
//					childrenNodes[chldType] = $('<div />');
//			}
//			childrenNodes[chldType].append(chldNode);
//		}
//		// Now append children nodes blocks to the current requirement node
//		for (var i = 0; i < childrenNodesOrder.length; ++i) {
//			var chldType = childrenNodesOrder[i];
//			if (childrenNodes[chldType]) {
//				newNode.append(childrenNodes[chldType]);
//			}
//		}
//

		var dchild = $("<span/>");
		dchild.attr("class","dummychildren");
		newNode.append(dchild);
		return newNode;
	};


    // Add test procedure node constructors
	// NOT USED - always top-level element
    constructNodeWrapperProcessor['TestProcedure'] = function (uuid) {
        return $('<div class="testprocedures"><div class="testprocedure-title">Test Procedure:</div></div>');
    };
    
    window.constructNodeProcessor['TestProcedure'] = function (nodeData, uuid) {
        // Fill in default values for missing attributes
//        if (!nodeData.attributes._description || !nodeData.attributes._description.value)
//            nodeData.attributes._description = { 'value': '&nbsp;' };
        var description;
        if( nodeData.attributes._description && nodeData.attributes._description.value )
            description = nodeData.attributes._description;
        else
            description = {"value":"&nbsp;","newvalue":"&nbsp;"};
        //description = description.replace(/  /g,"&nbsp;&nbsp;");
        var initcond;
        if( nodeData.attributes._initcond && nodeData.attributes._initcond.value )
            initcond = nodeData.attributes._initcond;
        else
            initcond = {"value":"&nbsp;","newvalue":"&nbsp;"};
        
        nodeData.id = nodeData.id.replace(/  /g,"&nbsp;&nbsp;");
        var nid = getNameOrId(nodeData);//.replace(/  /g,"&nbsp;&nbsp;");
		nodeData.attributes._nameOrId=getNameOrId(nodeData);
        
        if (!nodeData.attributes._status || !nodeData.attributes._status.value)
            nodeData.attributes._status = { 'value': 'in process' };
        // Construct HTML block
        var editable_prefix = (((readOnlyMode & EDIT_MODE_TPROC_RO) == 0) ? '' : 'non');
        var newNode = $(
            '<div class="tprocwrapper nodewrapper" id="block-' + uuid + '">' +
//              '<div style="height: 1px; width: 100%; border-top: solid 1px gray;"></div>' +
              '<div class="testprocedure supported-node" id="node-' + uuid + '">' +
                '<table cellpadding="0" cellspacing="0" border="0" width="100%" class="tprocid_wrap">' +
                  '<tr>' +
                    '<td>' +
                      '<img src="images/' + getNodeStatus('TestProcedure',nodeData.attributes._status.value).icon + '" alt="' + nodeData.attributes._status.value + '" title="' + nodeData.attributes._status.value + '" id="status-' + uuid + '" class="imgstatus" onclick="javascript:execNodeCommand(\'TestProcedure\', \'' + uuid + '\', \'chstatus\');" />' +
                    '</td>' +
                    '<td width="100%">' +
                    '<div class="hiddenPoint" id="' + uuid + '-attribute-_nameOrId-_raw" contenteditable="false">' +
                    nid.value +
                  '</div>' +
                      '<div class="tprocid nodetitle ' + editable_prefix + 'renamable ' + getNodeStatus('TestProcedure',nodeData.attributes._status.value).className + ' dragable tprocid-'+uuid+'" id="' + uuid + '-attribute-_nameOrId" contenteditable="false">' +
                        nid.newvalue +
                      '</div>' +
                    '</td>' +
                    '<td>' +
                      '<div class="tprocctrls nodectrls">' +
                        '<div style="text-align: right;">' +
                          '<img src="images/menu.png" alt="[v]" />' +
                        '</div>' +
                        '<div class="menu"  id="menu-'+uuid+'" style="display: none;">' +
                          constructMenu('TestProcedure', uuid) +
                        '</div>' +
                      '</div>' +
                    '</td>' +
                  '</tr>' +
                '</table>' +
                '<div class="hiddenPoint" id="' + uuid + '-attribute-_description-_raw">' +
                description.value +
              '</div>' +
                '<div class="tprocdesc nodedesc ' + editable_prefix + 'reditable tprocdesc-'+uuid+'" id="' + uuid + '-attribute-_description" contenteditable="false">' +
                  description.newvalue +
                '</div>' +
//                '<div class="tprocinitcondwrapper" style="display: ' + (initcond == '' || initcond == '&nbsp;' ? 'none' : 'block' ) + ';">' +
                '<div class="tprocinitcondwrapper">' +
                  '<b>Initial conditions: </b>' +
                  '<div class="hiddenPoint" id="' + uuid + '-attribute-_initcond-_raw">' +
                  initcond.value +
                '</div>' +
                  '<div class="tprocinitcond ' + editable_prefix + 'reditable tprocinitcond-'+uuid+'" id="' + uuid + '-attribute-_initcond" contenteditable="false">' +
                    initcond.newvalue +
                  '</div>' +
                '</div>' +  
              '</div>' +
            '</div>'
        );
        // Enumerate all children objects and recursively construct the rest of the tree
//        var childrenNodes = {};
//        for (var i in nodeData.children) {
//            // Construct the next child subtree (for requirements) or table (for test purposes)
//        	indexes[nodeData.children[i]] = parseInt(i)+1;
//            var chldNode = constructTree(nodeData.children[i]);
//            var chldType = chldNode.type;
//            if (!childrenNodes[chldType]) {
//                var wrapperConstructor = constructNodeWrapperProcessor[chldType];
//                if (wrapperConstructor)
//                    childrenNodes[chldType] = wrapperConstructor(uuid);
//                else
//                    childrenNodes[chldType] = $('<div />');
//            }
//            childrenNodes[chldType].append(chldNode);
//        }
//        // Now append children nodes blocks to the current requirement node
//        for (var i = 0; i < childrenNodesOrder.length; ++i) {
//            var chldType = childrenNodesOrder[i];
//            if (childrenNodes[chldType]) {
//                newNode.append(childrenNodes[chldType]);
//            }
//        }

		var dchild = $("<span/>");
		dchild.attr("class","dummychildren");
		newNode.append(dchild);
        return newNode;
    };
    

    // Add test procedure node constructors
    constructNodeWrapperProcessor['TestStep'] = function (uuid) {
        return $('<div class="children-TestStep" id="children-TestStep-'+uuid+'"></div>');
    };
    
    
    window.constructNodeProcessor['TestStep'] = function (nodeData, uuid) {
        // Fill in default values for missing attributes
        if (!nodeData.attributes._tstype || !nodeData.attributes._tstype.value)
            nodeData.attributes._tstype = { 'value': 'act' };
        if (!nodeData.attributes._description || !nodeData.attributes._description.value)
            nodeData.attributes._description = { 'value': '&nbsp;', 'newvalue': '&nbsp;' };
        if (!nodeData.attributes._status || !nodeData.attributes._status.value)
            nodeData.attributes._status = { 'value': 'in process' };
        // Construct HTML block
        var editable_prefix = (((readOnlyMode & EDIT_MODE_TSTEP_RO) == 0) ? ''
            : 'non');
        //nodeData.attributes._description.value = nodeData.attributes._description.value.replace(/  /g,"&nbsp;&nbsp;");
		if (nodeData.attributes._tstype.value == "verify"){
        	nodeData.attributes._description.value = 
        		updateLinkDataToDisplay(nodeData.attributes._description.value);
        }
        var string1 = '<div class="hiddenPoint" id="' + uuid + '-attribute-_description-_raw">'
        + nodeData.attributes._description.value + '</div><div class="tsdesc nodedesc ' + editable_prefix
                + 'reditable dragable tsdesc-' + uuid + '" id="' + uuid + '-attribute-_description" contenteditable="false">'
                + nodeData.attributes._description.newvalue + '</div>', string2 = '';
        if (nodeData.attributes._links != null &&
                nodeData.attributes._links.value != null &&
                    nodeData.attributes._tstype.value == "verify"){
                        m = null;
                        var a = nodeData.attributes._links.newvalue;
                        for(var k in a){
                        	if(m == null){
                        	   m = a[k];
                        	 }
                        }
                        if(m != null){
                        	var muuid = m.nodes[0];
                        	var mname = m.raw;
                        	if(m.raw != null){
	                        	if(mname){
		                            string2 = '<tr><td colspan="3">'+string1+'</td></tr>';
		                            string1 = formLinkTo(muuid, mname);
		                        }else
		                        {
	                              string2 = '<tr><td colspan="3">'+string1+'</td></tr>';
	                              string1 = markWrongLinks("", muuid, mname);
	                            }
	                        }else{
	                            string2 = '<tr><td colspan="3">'+string1+'</td></tr>';
	                            string1 = markWrongLinks("", muuid, mname);
	                            //string1 = '<span class = "clickable linkspan" id = "linkspan-' + muuid + '" onclick = "javascript:openEditorForNode(\'' + muuid + '\');">'
	                            //+ getUFName(m) + '</span>';
	                        }
                        }
                    }
//        var tsind = 1;
//        if(uuid in indexes){
//        	tsind = indexes[uuid];
//        }
//        if(tsind<10)
//        	tsind = "0"+tsind;
        var path = "";
        if(nodeData.path)
        	path = nodeData.path;
        var newNode = $('<div class="tswrapper nodewrapper" id="block-'+ uuid + '">'
                +
                // '<div style="height: 1px; width: 100%; border-top: solid 1px
                // gray;"></div>' +
                '<div class="teststep supported-node" id="node-'
                + uuid
                + '">'
                + '<table cellpadding="0" cellspacing="0" border="0" width="100%" class="tsid_wrap">'
                + '<tr>' +
                // '<td>' +
                // '<img src="images/' +
                // node_statuses['TestStep'][nodeData.attributes._status.value].icon
                // + '" alt="' + nodeData.attributes._status.value + '" title="'
                // + nodeData.attributes._status.value + '" id="status-' + uuid
                // + '" class="imgstatus"
                // onclick="javascript:execNodeCommand(\'TestStep\', \'' + uuid
                // + '\', \'chstatus\');" />' +
                // '</td>' +
                // '<td width="100%">' +
                // '<div class="tsid nodetitle ' + editable_prefix + 'renamable
                // ' +
                // node_statuses['TestStep'][nodeData.attributes._status.value].className
                // + '" id="tsid-' + uuid + '">' +
                // nodeData.id +
                // '</div>' +
                // '</td>' +
                '<td style="vertical-align: '
                + ((string2 == '') ? 'top' : 'center')
                + ";padding-top:4px"
                + ';"><span id = "' + uuid + '-attribute-_childIndex" class="teststepindex '+ uuid + '-attribute-_childIndex">'+getBetterPositionLabel(nodeData.attributes._childIndex.newvalue)+'</span><span class="tstype" id="tstype-' + uuid
                + '" onclick="javascript:execNodeCommand(\'TestStep\', \''
                + uuid + '\', \'chtstype\');">'
                + nodeData.attributes._tstype.value + '</span></td>'
                + '<td width="100%">' + string1 + '</td>'
                + '<td style="vertical-align: top;">'
                + '<div class="tsctrls nodectrls">'
                + '<div style="text-align: right;">'
                + '<img src="images/menu.png" alt="[v]" />' + '</div>'
                + '<div class="menu" id="menu-'+uuid+'" style="display: none;padding-top:4px">'
                + constructMenu('TestStep', uuid) + '</div>' + '</div>'
                + '</td>' + '</tr>' + string2 + '</table>' +
                // '<div class="tsdesc nodedesc ' + editable_prefix + 'editable"
                // id="tsdesc-' + uuid + '">' +
                // nodeData.attributes._description.value +
                // '</div>' +
                '</div>' + '</div>');
        if ((nodeData.attributes._tstype.value != "verify"))
            $(newNode.find('.menu-item-linktp')[0]).remove();
        if (string2 == "") {
            $(newNode.find('.menu-item-unlinktp')[0]).remove();
        } else {
            ($(newNode.find('.menu-item-linktp-name'))).html('Link other Test Purpose');
        }
        // Enumerate all children objects and recursively construct the rest of
        // the tree
//        var childrenNodes = {};
//        for ( var i in nodeData.children) {
//            // Construct the next child subtree (for requirements) or table (for
//            // test purposes)
//            var chldNode = constructTree(nodeData.children[i]);
//            var chldType = chldNode.type;
//            if (!childrenNodes[chldType]) {
//                var wrapperConstructor = constructNodeWrapperProcessor[chldType];
//                if (wrapperConstructor)
//                    childrenNodes[chldType] = wrapperConstructor(uuid);
//                else
//                    childrenNodes[chldType] = $('<div />');
//            }
//            childrenNodes[chldType].append(chldNode);
//        }
//        // Now append children nodes blocks to the current requirement node
//        for ( var i = 0; i < childrenNodesOrder.length; ++i) {
//            var chldType = childrenNodesOrder[i];
//            if (childrenNodes[chldType]) {
//                newNode.append(childrenNodes[chldType]);
//            }
//        
        // alert(nodeData.attributes._tstype.value);
        return newNode;
    };
    //actions before editor of test step opens
    //replaces 
    beforeEditorOpenAction['tsdesc'] = function(uuid) {
        //var req_uuid = extractUUID();
        uuid = extractUUID(uuid);
    	var nodeData = getNodeByUUID(uuid);
    	if (!nodeData.attributes._tstype || !nodeData.attributes._tstype.value)
            nodeData.attributes._tstype = { 'value': 'act' };
    	//if status is not "verify" then button needs to be hidden
        if(nodeData.attributes._tstype.value != 'verify'){
    		nodePlugins['tsdesc'] = '';
    	}else{
    		nodePlugins['tsdesc'] = nodeplugindefault;
    	}
    	//fromViewToData(uuid);
    };
    //plugin for linkto button
    //this is strange solution, but
    //it is used to diable
    //linkto to not "verify" Test Steps
    nodeplugindefault = 'testdb';
    //plugin for "linkto" button
    nodePlugins['tsdesc'] = nodeplugindefault;
    
	// Add class-to-type mapping for new node type
	nodeClassToType['testpurpose'] = 'TestPurpose';
	nodeClassToType['testprocedure'] = 'TestProcedure';
	nodeClassToType['teststep'] = 'TestStep';

	// Add name of the status attribute for the new node type
	statusAttr['TestPurpose'] = '_status';
    statusAttr['TestProcedure'] = '_status';
    statusAttr['TestStep'] = '_status';
    if(!window.attributeUpdate)
      window.attributeUpdate = [];
    window.attributeUpdate['_childIndex'] = function(uuid,name,raw,val){
      window.attributeUpdate['_default'](uuid,name,raw,getBetterPositionLabel(val));
    };
    window.attributeUpdate['_tstype'] = function(uuid,name,raw,val){
    	var node = getNodeByUUID(uuid);
    	window.attributeUpdate['_default'](uuid,name,raw,val);
    	if(node.attributes["_links"]&&node.attributes["_links"].value)
    		onChangeNode(uuid,false);
    };
    window.attributeUpdate['_links'] = function(uuid,name,raw,val){
    	var node = getNodeByUUID(uuid);
		if(raw==null && val == null)
			node.attributes[name] = null;
		else{
			node.attributes[name]={'newvalue':val,'value':raw};
		}
    	onChangeNode(uuid,false);
    };
    
	// Customize CKEditor style for editing test purpose ID
	CKEditorStyles['tpid'] = 'body { margin: 1px; font-weight: bold; }';
    CKEditorStyles['tprocid'] = 'body { margin: 1px; font-weight: bold; }';
    CKEditorStyles['tsdesc'] = 'body { margin: 1px; font-weight: bold; }';

	// Add specific dragging init handler
	draggingInitHandler['tpid'] = function (dragging_uuid, req_elems) {
		var req_uuid = extractUUID($('#node-' + dragging_uuid).closest('blockquote')[0].id);
		var underlying_req_idx = req_elems.index($('#node-' + req_uuid));
		if (underlying_req_idx < 0) {
			underlying_req_idx = null;
		}
		// Disallow dragging onto the parent requirement, and onto all
		// non-leaves
		$('#node-' + req_uuid + ', .collapsable > .requirement').addClass(
				'undroppable');
		// Set the disabled look for parent requirement and all non-leaves
		$('#block-' + req_uuid
						+ ', .collapsable > .requirement, .collapsable > .children-TestPurpose, .children-TestPurpose')
				.addClass('disabled');
		var toCheck = [];
		for ( var i = 0; i < req_elems.length; i++) {
			var kuuid = extractUUID(req_elems[i].id);
			toCheck.push(kuuid);
		}
		checkChilds(dragging_uuid, "", toCheck);
		return underlying_req_idx;
	};
	// Add specific dragging init handler
	draggingInitHandler['tsdesc'] = function(dragging_uuid, req_elems) {
		var req_uuid = extractUUID($('#node-' + dragging_uuid).closest(
				'div .nodewrapper')[0].id);
		var underlying_req_idx = req_elems.index($('#node-' + req_uuid));
		if (underlying_req_idx < 0) {
			underlying_req_idx = null;
		}
		return underlying_req_idx;
	};
	var basedesc = window.attributeUpdate['_description'];
	window.attributeUpdate['_description'] = function(uuid, name, raw, val){
		if(getType(uuid)=="TestPurpose"){
			window.attributeUpdate["_default"](uuid,"_ldescription",raw,val);
			var tpdesc = formTPDesc(uuid);
			basedesc(uuid,"_description",tpdesc['value'],tpdesc['newvalue']);
		}
		else
			basedesc(uuid,name,raw,val);	
	};
	window.attributeUpdate['_expectedResults']=function(uuid, name, raw, val){
		window.attributeUpdate["_default"](uuid,name,raw,val);
		var tpdesc = formTPDesc(uuid);
		basedesc(uuid,"_description",tpdesc['value'],tpdesc['newvalue']);
	};
	window.attributeUpdate['_childIndex']=function(uuid, name, raw, val){
		var node = getNodeByUUID(uuid);
		var elem = document.getElementById( uuid + '-attribute-_childIndex');
  		if(elem && node.attributes._type.value == "TestStep")
			elem.innerHTML = getBetterPositionLabel(val);
	};
	window.attributeUpdate['_status']=function(uuid, name, raw, val){
  		var node = getNodeByUUID(uuid);
  		var base = window.attributeUpdate['_status'];
  		if(node.attributes._type.value == "TestStep"){
  			//TODO redraw index 
  			onChangeNode(uuid,true);
  		}
  		else if (typeof base != "undefined")
  			base(uuid, name, raw, val);
	};
