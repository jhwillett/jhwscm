
FILES := $(shell ls -1 deps/*.url | sed 's:^deps/\(.*\).url$$:\1:g' )
JARS  := $(filter %.jar,$(FILES))
TBZS  := $(filter %.tar.bz2,$(FILES))
TGZS  := $(filter %.tar.gz,$(FILES))
ZIPS  := $(filter %.zip,$(FILES))

.PHONY: getdeps $(FILES:%=getdep-%)
getdeps: $(FILES:%=getdep-%)
$(FILES:%=getdep-%): getdep-%: $(DESTDIR)/%.crack.ok

# download the files
$(FILES:%=$(DESTDIR)/%): $(DESTDIR)/%: 
	mkdir -p $(dir $@)
	curl `cat deps/$*.url` --location --output $@.tmp
	mv $@.tmp $@

# confirm file signatures
$(FILES:%=$(DESTDIR)/%.md5.ok): $(DESTDIR)/%.md5.ok: $(DESTDIR)/%
	mkdir -p $(dir $@)
	md5sum $< | cut -f 1 -d ' ' > $@.md5.actual
	diff -wB deps/$(notdir $<).md5 $@.md5.actual
	touch $@

# open the files, varies by file type
$(JARS:%=$(DESTDIR)/%.crack.ok): $(DESTDIR)/%.crack.ok: $(DESTDIR)/%.md5.ok
	touch $@
$(TBZS:%=$(DESTDIR)/%.crack.ok): $(DESTDIR)/%.crack.ok: $(DESTDIR)/%.md5.ok
	cd $(DESTDIR) && tar xvfj $*
	touch $@
$(TGZS:%=$(DESTDIR)/%.crack.ok): $(DESTDIR)/%.crack.ok: $(DESTDIR)/%.md5.ok
	cd $(DESTDIR) && tar xvfz $*
	touch $@
$(ZIPS:%=$(DESTDIR)/%.crack.ok): $(DESTDIR)/%.crack.ok: $(DESTDIR)/%.md5.ok
	cd $(DESTDIR) && unzip $*
	touch $@
