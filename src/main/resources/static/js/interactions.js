(function () {
  'use strict';

  var csrfToken = (function () {
    var meta = document.querySelector('meta[name="_csrf"]');
    return meta ? meta.getAttribute('content') : '';
  })();

  function buildBody(extraParams) {
    var params = new URLSearchParams();
    params.append('_csrf', csrfToken);

    if (extraParams) {
      Object.keys(extraParams).forEach(function (key) {
        params.append(key, extraParams[key]);
      });
    }

    return params;
  }

  function postForm(url, extraParams) {
    return fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8'
      },
      body: buildBody(extraParams),
      credentials: 'same-origin',
      redirect: 'manual'
    });
  }

  function getCartBadgeCount() {
    var badge = document.querySelector('.header-shortcut strong');
    if (!badge) return 0;

    var count = parseInt(badge.textContent, 10);
    return isNaN(count) ? 0 : count;
  }

  function setCartBadge(count) {
    var badges = document.querySelectorAll('.header-shortcut strong');
    for (var i = 0; i < badges.length; i++) {
      badges[i].textContent = String(count);
    }
  }

  function getProductId(btn) {
    var id = parseInt(btn.getAttribute('data-product-id'), 10);
    return isNaN(id) ? null : id;
  }

  function setSavedState(btn, saved) {
    btn.classList.toggle('is-saved', saved);
    btn.textContent = saved
      ? (btn.getAttribute('data-saved-text') || 'Saved')
      : (btn.getAttribute('data-save-text') || 'Save');
  }

  function onSave(btn) {
    if (btn.getAttribute('data-loading') === 'true') return;

    var id = getProductId(btn);
    if (id === null) return;

    btn.setAttribute('data-loading', 'true');

    postForm('/saved/' + id + '/toggle').then(function () {
      setSavedState(btn, !btn.classList.contains('is-saved'));
    }).catch(function () {
      location.reload();
    }).finally(function () {
      btn.removeAttribute('data-loading');
    });
  }

  function onAddToCart(btn) {
    if (btn.getAttribute('data-loading') === 'true') return;

    var id = getProductId(btn);
    if (id === null) return;

    btn.setAttribute('data-loading', 'true');

    postForm('/cart/items/' + id, { quantity: 1 }).then(function () {
      setCartBadge(getCartBadgeCount() + 1);
    }).catch(function () {
      location.reload();
    }).finally(function () {
      btn.removeAttribute('data-loading');
    });
  }

  function onUnsave(btn) {
    if (btn.getAttribute('data-loading') === 'true') return;

    var id = getProductId(btn);
    if (id === null) return;

    btn.setAttribute('data-loading', 'true');

    postForm('/saved/' + id + '/toggle', { redirectTo: '/saved' }).then(function () {
      var row = btn.closest('tr, [data-product-row], article');
      if (row) {
        row.parentNode.removeChild(row);
      } else {
        setSavedState(btn, false);
      }
    }).catch(function () {
      location.reload();
    }).finally(function () {
      btn.removeAttribute('data-loading');
    });
  }

  document.addEventListener('click', function (e) {
    var btn = e.target.closest('[data-action]');
    if (!btn) return;

    e.preventDefault();

    var action = btn.getAttribute('data-action');
    if (action === 'save') onSave(btn);
    else if (action === 'add-to-cart') onAddToCart(btn);
    else if (action === 'unsave') onUnsave(btn);
  });
})();
