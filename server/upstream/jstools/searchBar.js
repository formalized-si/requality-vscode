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
			// auto focus search input field
			var inp = document.getElementById("search-value");
			if(inp){
				inp.focus();
				inp.select(); 
			}
		},
		hide: function(){
			var div = document.getElementById("searchBar");
			if(!div.classList.contains("search-hidden"))
				div.classList.add("search-hidden");
			var inp = document.getElementById("search-value");
			if(inp) inp.value = "";
		},
		nextCall: function(inp, cs){
		  	window.sb.search(inp, cs, false);
		},
		prevCall: function(inp, cs){
		  	window.sb.search(inp, cs, true);
		},
		fromTop: function(inp, cs){
			var sel = window.getSelection();
			if(sel) sel.removeAllRanges();
			window.sb.search(inp, cs, false);
		},
		search: function(inp, cs, backwards){
			var needle = inp ? inp.value : "";
			var casev = cs ? cs.checked : false;
			if(needle) window.sb.findText(needle, casev, backwards, true);
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
			// keep focus on the input field
			prev.addEventListener("mousedown", function(e){ e.preventDefault(); });
			prev.addEventListener("click", function(e){
			  e.preventDefault();
			  e.stopPropagation();
		      window.sb.prevCall(inp, casen);
			});
			var nxt = document.createElement("button");
			nxt.id="search-next";
			nxt.innerText="↓";
			nxt.addEventListener("mousedown", function(e){ e.preventDefault(); });
			nxt.addEventListener("click", function(e){
              e.preventDefault();
			  e.stopPropagation();
		      window.sb.nextCall(inp, casen);
			});
			//<input type="checkbox" id="scales" name="scales" checked>
			casen.id="caseSensitive";
			casen.checked = false;
			casen.type="checkbox";
			casen.addEventListener("change", function(){ window.sb.fromTop(inp, casen); });
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
			        if(event.shiftKey)
			    		window.sb.prevCall(inp, casen);
			    	else
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
			  // 70 is "f"; e.metaKey is `cmd` on macos; 114 is "F3"
			  if (e.keyCode == 70 && (e.ctrlKey || e.metaKey) || e.keyCode === 114) {
				window.sb.show();
			    e.preventDefault();
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