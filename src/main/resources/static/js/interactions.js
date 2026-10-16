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

  function postJson(url, extraParams, signal) {
    return fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
        'Accept': 'application/json'
      },
      body: buildBody(extraParams),
      credentials: 'same-origin',
      redirect: 'error',
      signal: signal
    }).then(function (response) {
      var contentType = response.headers.get('content-type') || '';
      if (response.redirected || contentType.indexOf('application/json') === -1) {
        throw new Error('The server returned an unexpected response. Please try again.');
      }
      return response.json().then(function (body) {
        if (!response.ok) {
          throw new Error(body.message || body.error || 'The request could not be completed.');
        }
        return body;
      });
    });
  }

  function setCartBadge(count) {
    var badges = document.querySelectorAll('.header-shortcut strong, [data-cart-count]');
    for (var i = 0; i < badges.length; i++) {
      badges[i].textContent = String(count);
    }
  }

  function setSavedBadge(count) {
    var badges = document.querySelectorAll('[data-saved-count]');
    for (var i = 0; i < badges.length; i++) badges[i].textContent = String(count);
  }

  function toast(message, isError) {
    var region = document.querySelector('[data-toast-region]');
    if (!region) {
      region = document.createElement('div');
      region.className = 'toast-region';
      region.setAttribute('data-toast-region', '');
      region.setAttribute('aria-live', 'polite');
      region.setAttribute('aria-atomic', 'true');
      document.body.appendChild(region);
    }
    var item = document.createElement('div');
    item.className = 'toast' + (isError ? ' is-error' : '');
    item.setAttribute('role', isError ? 'alert' : 'status');
    var copy = document.createElement('span');
    copy.textContent = message;
    var dismiss = document.createElement('button');
    dismiss.type = 'button';
    dismiss.className = 'toast-dismiss';
    dismiss.textContent = 'Dismiss';
    dismiss.addEventListener('click', function () { item.remove(); });
    item.appendChild(copy);
    item.appendChild(dismiss);
    region.appendChild(item);
    if (!isError) window.setTimeout(function () { if (item.parentNode) item.remove(); }, 7000);
  }

  function getProductId(btn) {
    var id = parseInt(btn.getAttribute('data-product-id'), 10);
    return isNaN(id) ? null : id;
  }

  function setSavedState(btn, saved) {
    btn.classList.toggle('is-saved', saved);
    btn.setAttribute('aria-pressed', String(saved));
    btn.textContent = saved
      ? (btn.getAttribute('data-saved-text') || 'Saved')
      : (btn.getAttribute('data-save-text') || 'Save');
  }

  function onSave(btn) {
    if (btn.disabled || btn.getAttribute('data-loading') === 'true') return;

    var id = getProductId(btn);
    if (id === null) return;

    var oldText = btn.textContent;
    btn.setAttribute('data-loading', 'true');
    btn.setAttribute('aria-busy', 'true');
    btn.disabled = true;
    btn.textContent = 'Saving...';

    postJson('/saved/' + id + '/toggle').then(function (body) {
      if (typeof body.saved !== 'boolean' || typeof body.savedItemCount !== 'number') {
        throw new Error('The server response did not include the saved state.');
      }
      setSavedState(btn, body.saved);
      setSavedBadge(body.savedItemCount);
      toast(body.saved ? 'Product saved.' : 'Product removed from saved items.', false);
    }).catch(function (error) {
      btn.textContent = oldText;
      toast(error.message, true);
    }).finally(function () {
      btn.removeAttribute('data-loading');
      btn.removeAttribute('aria-busy');
      btn.disabled = false;
    });
  }

  function formatPrice(value) {
    var amount = Number(value);
    return (isFinite(amount) ? Math.round(amount).toLocaleString('en-US') : '0') + ' VND';
  }

  function initializePurchaseForm() {
    var form = document.querySelector('[data-purchase-form]');
    if (!form) return;
    var select = form.querySelector('[name="variantId"]');
    var quantity = form.querySelector('[name="quantity"]');
    var submit = form.querySelector('.purchase-submit');
    var status = form.querySelector('[data-variant-status]');
    var price = document.querySelector('[data-variant-price]');
    var error = form.querySelector('[data-purchase-error]');
    var requestController = null;
    if (!select || !quantity) return;

    function updateSelection() {
      var option = select.options[select.selectedIndex];
      var stock = option && option.value ? parseInt(option.dataset.stock, 10) : 0;
      var valid = Boolean(option && option.value && !option.disabled && stock > 0);
      quantity.disabled = !valid;
      if (submit) submit.disabled = !valid;
      if (!valid) {
        quantity.removeAttribute('max');
        status.textContent = option && option.value ? 'This option is out of stock.' : 'Select an option to see availability.';
      } else {
        quantity.max = String(stock);
        if (Number(quantity.value) > stock || Number(quantity.value) < 1) quantity.value = '1';
        price.textContent = formatPrice(option.dataset.price);
        status.textContent = (stock <= 5 ? 'Only ' + stock + ' left. ' : stock + ' available. ') + 'SKU ' + option.dataset.sku + '.';
      }
      error.hidden = true;
    }

    select.addEventListener('change', function () {
      if (requestController) {
        requestController.abort();
        requestController = null;
      }
      updateSelection();
    });
    form.addEventListener('submit', function (event) {
      if (!submit) return;
      event.preventDefault();
      var option = select.options[select.selectedIndex];
      var stock = option ? parseInt(option.dataset.stock, 10) : 0;
      var amount = parseInt(quantity.value, 10);
      if (!option || !option.value || option.disabled || !Number.isInteger(amount) || amount < 1 || amount > stock) {
        error.textContent = 'Choose an available option and a quantity within its stock limit.';
        error.hidden = false;
        return;
      }
      if (requestController) requestController.abort();
      requestController = new AbortController();
      submit.disabled = true;
      submit.setAttribute('aria-busy', 'true');
      var label = submit.querySelector('[data-button-label]');
      if (label) label.textContent = 'Adding...';
      postJson(form.action, { variantId: option.value, quantity: amount }, requestController.signal).then(function (body) {
        if (typeof body.totalQuantity !== 'number') throw new Error('The server response did not include the cart total.');
        setCartBadge(body.totalQuantity);
        error.hidden = true;
        toast('Added to cart.', false);
      }).catch(function (requestError) {
        if (requestError.name === 'AbortError') return;
        error.textContent = requestError.message;
        error.hidden = false;
        toast(requestError.message, true);
      }).finally(function () {
        submit.removeAttribute('aria-busy');
        if (label) label.textContent = 'Add to cart';
        updateSelection();
      });
    });
    updateSelection();
  }

  function initializeImages() {
    document.querySelectorAll('.image-frame img').forEach(function (img) {
      function markMissing() {
        img.hidden = true;
        var fallback = img.parentElement.querySelector('.image-fallback');
        if (fallback) fallback.classList.add('is-visible');
      }
      function markLoaded() {
        img.hidden = false;
        var fallback = img.parentElement.querySelector('.image-fallback');
        if (fallback) fallback.classList.remove('is-visible');
      }
      img.addEventListener('error', markMissing);
      img.addEventListener('load', markLoaded);
      if (img.complete && img.naturalWidth === 0) markMissing();
    });
    var gallery = document.querySelector('[data-product-gallery]');
    if (!gallery) return;
    var main = gallery.querySelector('[data-gallery-main]');
    gallery.addEventListener('click', function (event) {
      var button = event.target.closest('[data-gallery-src]');
      if (!button || !main) return;
      main.hidden = false;
      var fallback = main.parentElement.querySelector('.image-fallback');
      if (fallback) fallback.classList.remove('is-visible');
      main.src = button.dataset.gallerySrc;
      gallery.querySelectorAll('[data-gallery-src]').forEach(function (item) {
        var active = item === button;
        item.classList.toggle('is-active', active);
        item.setAttribute('aria-pressed', String(active));
      });
    });
  }

  function initializeCarousel() {
    var track = document.querySelector('[data-carousel-track]');
    if (!track) return;
    var previous = document.querySelector('[data-carousel-previous]');
    var next = document.querySelector('[data-carousel-next]');
    var controls = document.querySelector('.carousel-controls');
    var reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (controls) controls.classList.add('is-ready');
    function updateButtons() {
      if (previous) previous.disabled = track.scrollLeft <= 2;
      if (next) next.disabled = track.scrollLeft + track.clientWidth >= track.scrollWidth - 2;
    }
    function move(direction) { track.scrollBy({ left: direction * track.clientWidth * 0.8, behavior: reduced ? 'auto' : 'smooth' }); }
    if (previous) previous.addEventListener('click', function () { move(-1); });
    if (next) next.addEventListener('click', function () { move(1); });
    track.addEventListener('scroll', updateButtons, { passive: true });
    window.addEventListener('resize', updateButtons);
    updateButtons();
  }

  function initializeCheckoutSubmission() {
    var form = document.querySelector('.checkout-form');
    if (!form) return;
    form.addEventListener('submit', function () {
      var button = form.querySelector('button[type="submit"]');
      if (!button || button.disabled || !form.checkValidity()) return;
      button.disabled = true;
      button.setAttribute('aria-busy', 'true');
      button.textContent = 'Confirming order...';
    });
  }

  function initializeProductVariantEditor() {
    var form = document.querySelector('[data-product-form]');
    if (!form) return;

    var list = form.querySelector('[data-variant-list]');
    var template = form.querySelector('[data-variant-template]');
    var addButton = form.querySelector('[data-add-variant]');
    var basePrice = form.querySelector('[data-base-price]');
    var maximumVariants = 100;

    if (!list || !template || !addButton || !basePrice) return;

    function rows() {
      return Array.prototype.slice.call(list.querySelectorAll('.variant-row'));
    }

    function reindexRows() {
      var currentRows = rows();

      currentRows.forEach(function (row, index) {
        row.setAttribute('data-variant-index', String(index));

        var title = row.querySelector('[data-variant-title]');
        if (title) title.textContent = index === 0 ? 'Default SKU' : 'Variant ' + (index + 1);

        var removeButton = row.querySelector('[data-remove-variant]');
        if (removeButton) {
          removeButton.disabled = currentRows.length === 1;
          removeButton.setAttribute('aria-label', 'Remove variant ' + (index + 1));
        }

        var fields = row.querySelectorAll('[data-variant-field]');
        for (var fieldIndex = 0; fieldIndex < fields.length; fieldIndex++) {
          var field = fields[fieldIndex];
          var fieldName = field.getAttribute('data-variant-field');
          var id = 'variants' + index + '.' + fieldName;
          var errorId = 'variant-' + index + '-' + fieldName + '-error';
          field.name = 'variants[' + index + '].' + fieldName;
          field.id = id;

          var label = field.closest('label');
          if (label) label.htmlFor = id;

          var error = row.querySelector('[data-variant-error="' + fieldName + '"]');
          if (error) {
            error.id = errorId;
            field.setAttribute('aria-describedby', errorId);
          }
        }
      });

      addButton.disabled = currentRows.length >= maximumVariants;
      addButton.setAttribute('aria-disabled', String(currentRows.length >= maximumVariants));
    }

    function firstVariantPrice() {
      var firstRow = rows()[0];
      return firstRow ? firstRow.querySelector('[data-variant-field="price"]') : null;
    }

    function synchronizeInitialPrice(forceVariantPrice) {
      var variantPrice = firstVariantPrice();
      if (!variantPrice) return;

      if (variantPrice.value !== '') {
        if (forceVariantPrice || basePrice.value === '') basePrice.value = variantPrice.value;
        return;
      }
      if (basePrice.value !== '') variantPrice.value = basePrice.value;
    }

    function initializeStockDefaults() {
      rows().forEach(function (row) {
        var stock = row.querySelector('[data-variant-field="stockQty"]');
        if (stock && stock.value === '' && stock.getAttribute('aria-invalid') !== 'true') {
          stock.value = '0';
        }
      });
    }

    function addVariant() {
      if (rows().length >= maximumVariants) return;

      var fragment = template.content.cloneNode(true);
      var row = fragment.querySelector('.variant-row');
      list.appendChild(fragment);
      reindexRows();

      if (rows().length === 1) synchronizeInitialPrice(false);

      var firstControl = row ? row.querySelector('select, input:not([type="hidden"])') : null;
      if (firstControl) firstControl.focus();
    }

    if (rows().length === 0) addVariant();
    else {
      reindexRows();
      initializeStockDefaults();
      synchronizeInitialPrice(false);
    }

    addButton.addEventListener('click', addVariant);

    list.addEventListener('click', function (event) {
      var removeButton = event.target.closest('[data-remove-variant]');
      if (!removeButton || rows().length === 1) return;

      var row = removeButton.closest('.variant-row');
      if (row) row.remove();
      reindexRows();
      synchronizeInitialPrice(true);
    });

    basePrice.addEventListener('input', function () {
      var variantPrice = firstVariantPrice();
      if (variantPrice) variantPrice.value = basePrice.value;
    });

    list.addEventListener('input', function (event) {
      if (event.target === firstVariantPrice()) basePrice.value = event.target.value;
    });

    form.addEventListener('submit', function () {
      var variantPrice = firstVariantPrice();
      if (variantPrice) basePrice.value = variantPrice.value;
    });
  }

  function initializePage() {
    initializeProductVariantEditor();
    initializePurchaseForm();
    initializeImages();
    initializeCarousel();
    initializeCheckoutSubmission();
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initializePage);
  else initializePage();

  document.addEventListener('click', function (e) {
    var btn = e.target.closest('[data-action]');
    if (!btn) return;

    e.preventDefault();

    var action = btn.getAttribute('data-action');
    if (action === 'save') onSave(btn);
  });
})();
