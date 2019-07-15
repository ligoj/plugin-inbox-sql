(function () {
    $('.menu-tools').prepend('<li class="bs-inbox"><a class="inbox-trigger" data-trigger="manual" data-toggle="popover" data-cascade="true" data-content="&nbsp;" data-ajax="/main/inbox" data-plugins="i18n,partial,css,js" data-html="html" data-title="<i class=\'fas fa-sync-alt faa-spin animated in spin fade\'></i>" role="button" data-placement="bottom"><i class="fas fa-inbox icon-info" data-container="body" data-toggle="tooltip" title="{{notifications}}" data-placement="bottom"></i><span class="label label-danger"><span class="count"></span></span></a></li>');
})();
