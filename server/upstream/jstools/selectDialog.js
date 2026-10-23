/**
 * Dialog module.
 * @module dialog.js
 * @version 1.0.0
 * @summary 02-01-2022
 * @author Mads Stoumann
 * @description Custom versions of `alert`, `confirm` and `prompt`, using `<dialog>`
 */
  function Dialog(settings) {
    this.settings = Object.assign(
      {
        accept: 'OK',
        bodyClass: 'dialog-open',
        cancel: 'Cancel',
        dialogClass: '',
        message: '',
        soundAccept: '',
        soundOpen: '',
        template: ''
      },
      settings
    )
    this.init();
  }
  Dialog.prototype.collectFormData = function(formData) {
    const object = {};
    
    $.each(formData,function(key, value) {
      if (!Reflect.has(object, key)) {
        object[key] = value
        return
      }
      if (!Array.isArray(object[key])) {
        object[key] = [object[key]]
      }
      object[key].push(value)
    });
    return object
  }

  Dialog.prototype.getFocusable = function() {
    return this.dialog.querySelectorAll('button,[href],select,textarea,input:not([type="hidden"]),[tabindex]:not([tabindex="-1"])');
  }

  Dialog.prototype.init = function () {
    this.dialogSupported = typeof HTMLDialogElement === 'function'
    this.dialog = document.createElement('dialog')
    this.dialog.role = 'dialog'
    this.dialog.dataset.component = this.dialogSupported ? 'dialog' : 'no-dialog';
    this.dialog.innerHTML = 
    '<form method="dialog" data-ref="form">'+
    ' <fieldset data-ref="fieldset" role="document">'+
    '    <legend data-ref="message" id="${(Math.round(Date.now())).toString(36)}"></legend>'+
    '    <div data-ref="template"></div>'+
    '  </fieldset>'+
    '  <menu> '+
    '    <button'+(this.dialogSupported ? '' : ' type="button"')+' data-ref="cancel" value="cancel"></button>'+
    '    <button'+(this.dialogSupported ? '' : ' type="button"')+' data-ref="accept" value="default"></button>'+
    '  </menu>'+
    '  <audio data-ref="soundAccept"></audio>'+
    '<audio data-ref="soundOpen"></audio>'+
    '</form>';
    document.body.appendChild(this.dialog)

    this.elements = {}
    this.focusable = []
    
    $.each(this.dialog.querySelectorAll('[data-ref]'),function(key, el) {
		this.elements[el.dataset.ref] = el
		});
    this.dialog.setAttribute('aria-labelledby', this.elements.message.id)
    this.elements.cancel.addEventListener('click', function() { this.dialog.dispatchEvent(new Event('cancel')) })
    this.dialog.addEventListener('keydown', function(e) {
      if (e.key === 'Enter') {
        if (!this.dialogSupported) e.preventDefault()
        this.elements.accept.dispatchEvent(new Event('click'))
      }
      if (e.key === 'Escape') this.dialog.dispatchEvent(new Event('cancel'))
      if (e.key === 'Tab') {
        e.preventDefault()
        const len =  this.focusable.length - 1;
        let index = this.focusable.indexOf(e.target);
        index = e.shiftKey ? index - 1 : index + 1;
        if (index < 0) index = len;
        if (index > len) index = 0;
        this.focusable[index].focus();
      }
    })
    this.toggle()
  }

  Dialog.prototype.open = function(settings) {
    const dialog = Object.assign({}, this.settings, settings)
    this.dialog.className = dialog.dialogClass || ''
    this.elements.accept.innerText = dialog.accept
    this.elements.cancel.innerText = dialog.cancel
    this.elements.cancel.hidden = dialog.cancel === ''
    this.elements.message.innerText = dialog.message
    this.elements.soundAccept.src = dialog.soundAccept || ''
    this.elements.soundOpen.src = dialog.soundOpen || ''
    this.elements.target = dialog.target || ''
    this.elements.template.innerHTML = '<div id="lazy" style="overflow:auto; border:1px solid silver; min-height:100px;"/>'

    this.focusable = this.getFocusable()
    this.hasFormData = this.elements.fieldset.elements.length > 0

    if (dialog.soundOpen) {
      this.elements.soundOpen.play()
    }

    this.toggle(true)


	var oldpath = window.location.pathname;
	var paths = oldpath.split("/"); 
	// lazy demo
	var resPath = window.location.protocol + "//" + window.location.host + "" + paths[0]+"/"+paths[1]+"/markup/"+paths[3] +"/";
	var newURL = window.location.protocol + "//" + window.location.host + "" + paths[0]+"/"+paths[1]+"/tree/"+paths[3] +"/";
	$('#lazy').jstree({
        'plugins' : ['themes','ui'],
        
	    "types" : {
	        "Requirement" : {
	            "icon" : "Requirement.png"
	        }
	    },
		'core' : {
			'multiple' : false,
			'data' : {
				"url" : newURL,
				"data" : function (node) {//	alert(resPath+"images/nodetypes/"+node.type+".png");
					return { "id" : node.uuid, "type": node.type, "text":node.text+"["+node.type+"]" };
				}
			}
		},
	});

    if (this.hasFormData) {
      this.focusable[0].focus()
      this.focusable[0].select()
    }
    else {
      this.elements.accept.focus()
    }
  }

  Dialog.prototype.toggle = function(open) {
    if (this.dialogSupported && open) this.dialog.showModal()
    if (!this.dialogSupported) {
      document.body.classList.toggle(this.settings.bodyClass, open)
      this.dialog.hidden = !open
      if (this.elements.target && !open) {
        this.elements.target.focus()
      }
    }
  }

  Dialog.prototype.waitForUser = function() {
//    return new Promise(resolve => {
//      this.dialog.addEventListener('cancel', () => { 
//        this.toggle()
//        resolve(false)
//      }, { once: true })
//      this.elements.accept.addEventListener('click', () => {
//        let value = this.hasFormData ? this.collectFormData(new FormData(this.elements.form)) : true;
//        if (this.elements.soundAccept.getAttribute('src').length > 0) this.elements.soundAccept.play()
//        this.toggle()
//        resolve(value)
//      }, { once: true })
//    })
  }

  Dialog.prototype.alert = function(message, config) {
    //const settings = Object.assign({}, config, { cancel: '', message, template: '' })
    //this.open(settings)
    return this.waitForUser()
  }

  Dialog.prototype.confirm = function(message, config) {
    //const settings = Object.assign({}, config, { message, template: '' })
    //this.open(settings)
    return this.waitForUser()
  }

  Dialog.prototype.prompt = function(message, value, config) {
    //const template = `<label aria-label="${message}"><input type="text" name="prompt" value="${value}"></label>`
    //const settings = Object.assign({}, config, { message, template })
    //this.open(settings)
    return this.waitForUser()
  }

/* FOR DEMO */
//  $( window ).on( "load", function() {
//	const dialog = new Dialog();
//	  dialog.open({
//	    accept: 'Sign in',
//	    dialogClass: 'custom',
//	    message: 'Please select a requirement',
//	  })
//	  dialog.waitForUser().then((res) => {  console.log(res) })
  //});