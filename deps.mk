
FILES += junit-4.8.2.jar
FILES += cobertura-1.9.4.1-bin.tar.bz2
FILES += pmd-bin-4.2.5.zip
FILES += jdepend-2.9.zip
FILES += javancss-32.53.zip
FILES += checkstyle-5.2-bin.tar.gz

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
	curl `cat $*.url` --location --output $@.tmp
	mv $@.tmp $@

# confirm file signatures
$(FILES:%=$(DESTDIR)/%.md5.ok): $(DESTDIR)/%.md5.ok: $(DESTDIR)/%
	mkdir -p $(dir $@)
	md5sum $< | cut -f 1 -d ' ' > $@.md5.actual
	diff -wB $(notdir $<).md5 $@.md5.actual
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
