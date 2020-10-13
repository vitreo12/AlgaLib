+ SynthDef {
	writeDef { arg file;
		// This describes the file format for the synthdef files.
		var allControlNamesTemp, allControlNamesMap;

		try {
			file.putPascalString(name.asString);

			this.writeConstants(file);

			//controls have been added by the Control UGens
			file.putInt32(controls.size.asInteger);
			controls.do { | item |
				file.putFloat(item.asFloat);
			};

			allControlNamesTemp = allControlNames.reject { |cn| cn.rate == \noncontrol };
			file.putInt32(allControlNamesTemp.size.asInteger);
			allControlNamesTemp.do { | item |
				if (item.name.notNil) {
					file.putPascalString(item.name.asString);
					file.putInt32(item.index.asInteger);
				};
			};

			file.putInt32(children.size.asInteger);
			children.do { | item |
				item.writeDef(file);
			};

			file.putInt16(variants.size.asInteger);
			if (variants.size > 0) {
				allControlNamesMap = ();
				allControlNamesTemp.do { |cn|
					allControlNamesMap[cn.name] = cn;
				};
				variants.keysValuesDo {|varname, pairs|
					var varcontrols;

					varname = name ++ "." ++ varname;
					if (varname.size > 32) {
						Post << "variant '" << varname << "' name too long.\n";
						^nil
					};
					varcontrols = controls.copy;
					pairs.pairsDo { |cname, values|
						var cn, index;
						cn = allControlNamesMap[cname];
						if (cn.notNil) {
							values = values.asArray;
							if (values.size > cn.defaultValue.asArray.size) {
								postf("variant: '%' control: '%' size mismatch.\n",
									varname, cname);
								^nil
							}{
								index = cn.index;
								values.do {|val, i|
									varcontrols[index + i] = val;
								}
							}
						}{
							postf("variant: '%' control: '%' not found.\n",
								varname, cname);
							^nil
						}
					};
					file.putPascalString(varname.asString);
					varcontrols.do { | item |
						file.putFloat(item.asFloat);
					};
				};
			};
		} { // catch
			arg e;

			Error("SynthDef: could not write def: %".format(e.what())).throw;
		}
	}
}