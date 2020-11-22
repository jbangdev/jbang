;(function () {
  'use strict'
  // NOTE: v4-shims are required to support the output of the icon macro generated from AsciiDoc content
  require('@fortawesome/fontawesome-free/js/v4-shims')
  var fa = require('@fortawesome/fontawesome-svg-core')

  ;(window.FontAwesomeIconDefs || []).forEach(function (faIconDef) {
    fa.library.add(faIconDef)
  })

  fa.dom.i2svg()
  delete window.___FONT_AWESOME___
  delete window.FontAwesomeIconDefs
})()
