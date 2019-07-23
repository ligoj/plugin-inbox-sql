(function () {
    $('.menu-tools').prepend('<li class="bs-inbox"><a class="inbox-trigger" data-trigger="manual" data-toggle="popover" data-cascade="true" data-content="&nbsp;" data-ajax="/main/inbox" data-plugins="i18n,partial,css,js" data-html="html" data-title="<i class=\'fas fa-sync-alt faa-spin animated in spin fade\'></i>" role="button" data-placement="bottom"><i class="fas fa-inbox icon-info" data-container="body" data-toggle="tooltip" title="{{notifications}}" data-placement="bottom"></i><span class="label label-danger"><span class="count"></span></span></a></li>');
    var $cascade = applicationManager.$cascade;
    ((($cascade.ext = $cascade.ext ||{})['plugin-inbox-sql'] = {}).updateMessageCounter = function() {
        var count = $cascade.session.userSettings.unreadMessages || 0;
        var $count = $('.menu-tools').find('.bs-inbox .count');
        if (count) {
            $count.html(count > 99 ? '&#8734;' : count).closest('.label').removeClass('hidden');
        } else {
            $count.empty().closest('.label').addClass('hidden');
        }
    })();
})();
