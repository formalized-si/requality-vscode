try{
	function SearchBar(){
		
	}
	SearchBar.prototype = {
		findText: function(aString, aCaseSensitive, aBackwards, aWrapAround) {
		  return window.find(aString, aCaseSensitive, aBackwards, aWrapAround);
		},
		show: function(){
			var div = document.getElementById("searchBar");
			if(div.classList.contains("search-hidden"))
				div.classList.remove("search-hidden");
			
		},
		hide: function(){
			var div = document.getElementById("searchBar");
			if(!div.classList.contains("search-hidden"))
				div.classList.add("search-hidden");
		},
		nextCall: function(inp, cs){
		  var casev = false;
		  if(cs && cs.value)
		    casev = cs.checked;
		  if(inp && inp.value)
		  	window.sb.findText(inp.value, casev, false, true);
		},
		prevCall: function(inp, cs){
		  var casev = false;
		  if(cs && cs.value)
		    casev = cs.checked;
		  if(inp && inp.value)
		   	window.sb.findText(inp.value, casev, true, true);
		},
		init: function(){
			var div = document.createElement("div");
		    div.classList.add("search-hidden");
			div.id = "searchBar"
			var form = document.createElement("span");
			var inp = document.createElement("input");
			inp.id = "search-value";
			inp.type = "text";
			inp.classList.add("search-input");
			var prev = document.createElement("button");
			prev.id="search-prev";
			prev.innerText="↑";
			var casen = document.createElement("input"); 
			prev.addEventListener("click", function(e){
			  e.preventDefault();
			  e.stopPropagation();
		      window.sb.prevCall(inp, casen);
			});
			var nxt = document.createElement("button");
			nxt.id="search-next";
			nxt.innerText="↓";
			nxt.addEventListener("click", function(e){
              e.preventDefault();
			  e.stopPropagation();
		      window.sb.nextCall(inp, casen);
			});
			//<input type="checkbox" id="scales" name="scales" checked>
			casen.id="caseSensitive";
			casen.checked = false;
			casen.type="checkbox";
			// <label for="scales">
			var caseLab = document.createElement("label");
			caseLab.for = "caseSensitive";
			caseLab.innerHTML = "Учитывать регистр";
			
			var cls = document.createElement("button");
			cls.id="close";
			cls.innerHTML="X";
			cls.addEventListener("click", function(e){
			    e.preventDefault();
			    e.stopPropagation();
				window.sb.hide();
			});
			var keyPressedEvent = function(event){
				event = event || window.event;
		    	var escapePressed = false;
		    	if ("key" in event) 
		        	escapePressed = (event.key === "Escape" || event.key === "Esc");
		    	else 
			        escapePressed = (event.keyCode === 27);
		    	var enterPressed = false;
		    	if ("key" in event) 
		        	enterPressed = (event.key === "Enter" || event.key === "Ent");
		    	else 
			        enterPressed = (event.keyCode === 13);
			    if(escapePressed){
			        event.preventDefault();
			        event.stopPropagation();
			    	window.sb.hide();
			    }
			    if(enterPressed && (!document.getElementById("searchBar")||!document.getElementById("searchBar").classList.contains("search-hidden"))){
			        event.preventDefault();
			        event.stopPropagation();
			    	window.sb.nextCall(inp, casen);
			    }
			};
			div.onkeydown = keyPressedEvent;
			inp.onkeydown = keyPressedEvent;
			
			//form.attributes["action"]="#";
			form.appendChild(inp);
			form.appendChild(prev);
			form.appendChild(nxt);
			form.appendChild(casen);
			form.appendChild(caseLab);
			form.appendChild(cls);
			div.appendChild(form);
			document.body.appendChild(div);
			document.addEventListener("keydown", function(e) {
			  if (e.keyCode == 70 && e.ctrlKey || e.keyCode === 114) {
				window.sb.show();
			    e.preventDefault();
			    keyPressedEvent(e);
			  }
			});
		}
	}
}catch(eaxa){
	alert(eaxa+" "+eaxa.stack);
}

window.addEventListener('load', function() {
  if(isInTool()){
    window.sb = new SearchBar();
    window.sb.init();
  }
});