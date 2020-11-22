var hash = window.location.hash
var queueData = []
var checkActiveClass
var tabOnLargeScreen = 5 // Display tab on desktop
var tabOnSmallScreen = 1 // display tab on mobile view bewlow 768px screen
var smallBreak = 768 // Your small screen breakpoint in pixels
find('.doc .tabset').forEach(function (tabset) {
  var active
  var tabs = tabset.querySelector('.tabs')
  if (tabs) {
    var first
    find('ul', tabs).forEach(function (ulTabSet, id) {
      if (ulTabSet) {
        find('li', ulTabSet).forEach(function (tab, idx) {
          var id = (tab.querySelector('a[id]') || tab).id

          if (window.innerWidth < smallBreak) {
            if (idx > tabOnSmallScreen - 1) {
              queueData.push(tab)
            }
          } else {
            if (idx > tabOnLargeScreen - 1) {
              queueData.push(tab)
            }
          }

          checkActiveClass = setTimeout(function () {
            var activeTabList = tab.classList.contains('is-active')

            if (activeTabList) {
              if (queueData.length > 0) {
                if (window.innerWidth > smallBreak && (tab.parentNode.childElementCount > tabOnLargeScreen - 1)) {
                  tab.parentNode.parentNode.insertAdjacentHTML(
                    'beforeend',
                    /*eslint max-len: ["error", { "code": 180 }]*/
                    '<div class="other-tab-box"><a href="#" class="dropddown-btn dropdown-btn-down">More... </a> <ul class="other-tablist"></ul></div>'
                  )
                } else {
                  tab.parentNode.parentNode.insertAdjacentHTML(
                    'beforeend',
                    /*eslint max-len: ["error", { "code": 180 }]*/
                    '<div class="other-tab-box desktop-hide"><a href="#" class="dropddown-btn dropdown-btn-down">More... </a> <ul class="other-tablist"></ul></div>'
                  )
                }
                var dropdownBtn = tab.parentNode.parentNode.querySelector('.dropdown-btn-down')
                var dropdownMenu = tab.parentNode.parentNode.querySelector('.tabs .other-tablist')
                dropdownBtn.addEventListener('click', function (e) {
                  // console.log(e, 'enter')
                  e.preventDefault()
                  if (dropdownMenu.style.display === 'block' || dropdownMenu.classList.contains('show')) {
                    dropdownMenu.classList.remove('show')
                    dropdownMenu.classList.add('hide')
                  } else {
                    dropdownMenu.classList.add('show')
                    dropdownMenu.classList.remove('hide')
                  }
                })
              }
            }
          }, 40)

          if (!id) return
          var pane = getPane(id, tabset)
          if (!idx) first = { tab: tab, pane: pane }
          if (!active && hash === '#' + id && (active = true)) {
            tab.classList.add('is-active')
            if (pane) pane.classList.add('is-active')
          } else if (!idx) {
            tab.classList.remove('is-active')
            if (pane) pane.classList.remove('is-active')
          }
          tab.addEventListener('click', activateTab.bind({ tabset: tabset, tab: tab, pane: pane }))
        })
      }
    })

    if (!active && first) {
      first.tab.classList.add('is-active')
      if (first.pane) first.pane.classList.add('is-active')
    }
  }

  tabset.classList.remove('is-loading')
  clearTimeout(checkActiveClass, 20000)
})

setTimeout(function () {
  if (queueData.length) {
    queueData.forEach(function (tablist) {
      tablist.parentNode.nextElementSibling.lastElementChild.appendChild(tablist)
    })
  }
}, 200)
// to activate tabset
function activateTab (e) {
  e.preventDefault()
  var tab = this.tab
  var pane = this.pane
  // Moving element from dropdown into tabset list
  var tabMenu = document.querySelector('.tabs ul')
  var nodeTab = tab.parentNode.parentNode.parentNode.querySelector('.tabs > ul')
  var nodeDropdownTabNode = tab.parentNode
  if (tab.parentNode.classList[0] === 'other-tablist') {
    nodeDropdownTabNode.appendChild(nodeTab.lastElementChild)
    nodeTab.appendChild(tab)
    nodeDropdownTabNode.classList.remove('show')
  }
  var activeTabListItem = tab.classList.contains('is-active')
  if (activeTabListItem) {
    tabMenu.classList.remove('show')
  }
  // for tab active pane
  find('.tabs li, .tab-pane', this.tabset).forEach(function (it) {
    it === tab || it === pane ? it.classList.add('is-active') : it.classList.remove('is-active')
  })
}

function find (selector, from) {
  return Array.prototype.slice.call((from || document).querySelectorAll(selector))
}
setTimeout(function () {
  if (document.querySelector(' .dropddown-btn')) {
    document.querySelector(' .dropddown-btn').addEventListener('click', function (e) {
      e.preventDefault()
    })
  }
}, 200)

function getPane (id, tabset) {
  return find('.tab-pane', tabset).find(function (it) {
    return it.getAttribute('aria-labelledby') === id
  })
}
