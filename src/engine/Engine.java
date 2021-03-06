package engine;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;

import javax.swing.JOptionPane;

import data.Constants;
import data.Constants.BaseDataFields;
import data.EvoLines;
import data.Names;
import data.Pokemon;
import data.Settings;

public class Engine {

	static boolean verifyRom (FileChannel chin) throws IOException {

		byte[] header = {0x50, 0x4D, 0x5F, 0x43, 0x52, 0x59, 0x53, 0x54, 0x41, 0x4C, 0x00, 0x41, 0x42,
			0x43, 0x44, (byte) 0xC0, 0x30, 0x31, 0x00, 0x10, 0x06, 0x03, 0x01, 0x33, 0x00};

		ByteBuffer headerin = ByteBuffer.allocate(header.length);
		chin.read(headerin, 0x134);

		return ((ByteBuffer) headerin.rewind()).compareTo(ByteBuffer.wrap(header)) == 0;
	}

	static void createRomCopy (FileChannel chin, FileChannel chout) throws IOException {

		chin.transferTo(0, chin.size(), chout);
	}

	static void readData (FileChannel ch, ByteBuffer bbRead, ByteBuffer bbWrite, long pos) throws IOException {

		ch.position(pos);
		bbRead.rewind();
		ch.read(bbRead);

		ch.position(pos);
		bbWrite.rewind();
		ch.read(bbWrite);
	}

	static void readData (FileChannel ch, ByteBuffer bb, long pos) throws IOException {

		ch.position(pos);
		bb.rewind();
		ch.read(bb);
	}

	static void fuseNames (FileChannel chout, ByteBuffer out, int[] fusionIds) throws IOException {

		byte[] name = new byte[Constants.NAMES_LENGTH];

		for (int i = Pokemon.BULBASAUR.ordinal() ; i <= Pokemon.CELEBI.ordinal() ; i ++) {

			Arrays.fill(name, (byte) 0x50);

			if (i == Pokemon.UNOWN.ordinal()) i ++;

			byte[] prefix = Names.values()[i].getPrefix();
			byte[] suffix = Names.values()[fusionIds[i]].getSuffix();

			prefix = Arrays.copyOf(prefix, prefix.length - 1); // get rid of the terminator

			if ((prefix.length + suffix.length) > 10)
				suffix = Arrays.copyOf(suffix, 5); // get rid of the terminator if no need for it

			System.arraycopy(prefix, 0, name, 0, prefix.length);
			System.arraycopy(suffix, 0, name, prefix.length, suffix.length);

			out.position(i * Constants.NAMES_LENGTH);
			out.put(name);

			// while we're at it, directly replace hardcoded names in the game corner

			for (int j = 0 ; j < name.length ; j ++) {
				if (name[j] == 0x50) name[j] = 0x7f;
			}

			if (i == Pokemon.ABRA.ordinal())
				writeToRom(chout, Constants.GOLDENROD_GAME_CORNER, name);

			if (i == Pokemon.CUBONE.ordinal())
				writeToRom(chout, Constants.GOLDENROD_GAME_CORNER + 16, name);

			if (i == Pokemon.WOBBUFFET.ordinal())
				writeToRom(chout, Constants.GOLDENROD_GAME_CORNER + 32, name);

			if (i == Pokemon.PIKACHU.ordinal())
				writeToRom(chout, Constants.CELADON_GAME_CORNER, name);

			if (i == Pokemon.PORYGON.ordinal())
				writeToRom(chout, Constants.CELADON_GAME_CORNER + 16, name);

			if (i == Pokemon.LARVITAR.ordinal())
				writeToRom(chout, Constants.CELADON_GAME_CORNER + 32, name);
		}
	}

	static void switchPalettes (ByteBuffer in, ByteBuffer out, int[] fusionIds) {

		byte[] pal = new byte[Constants.PALETTES_LENGTH];

		for (int i = Pokemon.BULBASAUR.ordinal() ; i <= Pokemon.CELEBI.ordinal() ; i ++) {

			if (i == Pokemon.UNOWN.ordinal()) i ++;

			in.position(fusionIds[i] * Constants.PALETTES_LENGTH);
			in.get(pal);

			out.position(i * Constants.PALETTES_LENGTH);
			out.put(pal);
		}
	}

	static int baseDataPos (int curMon, BaseDataFields field) {

		return curMon * Constants.BASE_DATA_LENGTH + field.getOffset();
	}

	static void fuseBaseData (ByteBuffer in, ByteBuffer out, int[] fusionIds) {

		for (int i = Pokemon.BULBASAUR.ordinal() ; i <= Pokemon.CELEBI.ordinal() ; i ++) {

			if (i == Pokemon.UNOWN.ordinal()) i ++;

			BaseData.fuseStats(in, out, fusionIds, baseDataPos(i, BaseDataFields.STATS), baseDataPos(fusionIds[i], BaseDataFields.STATS));
			BaseData.fuseTypes(in, out, fusionIds, baseDataPos(i, BaseDataFields.TYPE_1), baseDataPos(fusionIds[i], BaseDataFields.TYPE_1));
			BaseData.fuseCatchRates(in, out, fusionIds, baseDataPos(i, BaseDataFields.CATCH_RATE), baseDataPos(fusionIds[i], BaseDataFields.CATCH_RATE));
			BaseData.fuseBaseExp(in, out, fusionIds, baseDataPos(i, BaseDataFields.BASE_EXP), baseDataPos(fusionIds[i], BaseDataFields.BASE_EXP));
			BaseData.fuseHeldItems(in, out, fusionIds, baseDataPos(i, BaseDataFields.ITEM_1), baseDataPos(fusionIds[i], BaseDataFields.ITEM_1));
			BaseData.fuseGenders(in, out, fusionIds, baseDataPos(i, BaseDataFields.GENDER), baseDataPos(fusionIds[i], BaseDataFields.GENDER));
			BaseData.fuseHatchCycles(in, out, fusionIds, baseDataPos(i, BaseDataFields.HATCH_CYCLES), baseDataPos(fusionIds[i], BaseDataFields.HATCH_CYCLES));
			BaseData.fuseGrowthRates(in, out, fusionIds, baseDataPos(i, BaseDataFields.GROWTH_RATE), baseDataPos(fusionIds[i], BaseDataFields.GROWTH_RATE));
			BaseData.fuseEggGroups(in, out, fusionIds, baseDataPos(i, BaseDataFields.EGG_GROUPS), baseDataPos(fusionIds[i], BaseDataFields.EGG_GROUPS));
			BaseData.combineTmHm(in, out, fusionIds, baseDataPos(i, BaseDataFields.TMHM), baseDataPos(fusionIds[i], BaseDataFields.TMHM));
		}
	}

	static void copyData (FileChannel ch, ByteBuffer bbWrite, long pos) throws IOException {

		ch.position(pos);
		bbWrite.rewind();
		ch.write(bbWrite);
	}

	static void writeToRom (FileChannel ch, long pos, byte[] bytes) throws IOException {

		ch.position(pos);
		ch.write(ByteBuffer.wrap(bytes));
	}

	static void setSlowpokeSlowing (FileChannel ch) throws IOException {

		// EVOLVE_LEVEL, 37, SLOWBRO -> EVOLVE_LEVEL, 37, SLOWKING
		byte[] bytes = {(byte) (Pokemon.SLOWKING.ordinal() + 1)};
		writeToRom(ch, Constants.EVOS_ATTACKS + Constants.EVOS_ATTACKS_LENGTH * Pokemon.SLOWPOKE.ordinal() + 2, bytes);
	}

	static void setGloomBellossom (FileChannel ch) throws IOException {

		// EVOLVE_ITEM, LEAF_STONE, VILEPLUME -> EVOLVE_ITEM, SUN_STONE, BELLOSSOM
		byte[] bytes = {Constants.SUN_STONE, (byte) (Pokemon.BELLOSSOM.ordinal() + 1)};
		writeToRom(ch, Constants.EVOS_ATTACKS + Constants.EVOS_ATTACKS_LENGTH * Pokemon.GLOOM.ordinal() + 1, bytes);
	}

	static void setPoliwhirlPolitoed (FileChannel ch) throws IOException {

		// EVOLVE_ITEM, WATER_STONE, POLIWRATH -> EVOLVE_ITEM, WATER_STONE, POLITOED
		byte[] bytes = {(byte) (Pokemon.POLITOED.ordinal() + 1)};
		writeToRom(ch, Constants.EVOS_ATTACKS + Constants.EVOS_ATTACKS_LENGTH * Pokemon.POLIWHIRL.ordinal() + 2, bytes);
	}

	static void setTyrogueHitmonlee (FileChannel ch) throws IOException {

		// EVOLVE_LEVEL, 20, HITMONTOP -> EVOLVE_LEVEL, 20, HITMONLEE
		byte[] bytes = {(byte) (Pokemon.HITMONLEE.ordinal() + 1)};
		writeToRom(ch, Constants.EVOS_ATTACKS + Constants.EVOS_ATTACKS_LENGTH * Pokemon.TYROGUE.ordinal() + 2, bytes);
	}

	static void setTyrogueHitmonchan (FileChannel ch) throws IOException {

		// EVOLVE_LEVEL, 20, HITMONTOP -> EVOLVE_LEVEL, 20, HITMONCHAN
		byte[] bytes = {(byte) (Pokemon.HITMONCHAN.ordinal() + 1)};
		writeToRom(ch, Constants.EVOS_ATTACKS + Constants.EVOS_ATTACKS_LENGTH * Pokemon.TYROGUE.ordinal() + 2, bytes);
	}

	static void setEeveeJolteon (FileChannel ch) throws IOException {

		// EVOLVE_ITEM, WATER_STONE, VAPOREON -> EVOLVE_ITEM, THUNDERSTONE, JOLTEON
		byte[] bytes = {Constants.THUNDERSTONE, (byte) (Pokemon.JOLTEON.ordinal() + 1)};
		writeToRom(ch, Constants.EVOS_ATTACKS + Constants.EVOS_ATTACKS_LENGTH * Pokemon.EEVEE.ordinal() + 1, bytes);
	}

	static void setEeveeFlareon (FileChannel ch) throws IOException {

		// EVOLVE_ITEM, WATER_STONE, VAPOREON -> EVOLVE_ITEM, FIRE_STONE, FLAREON
		byte[] bytes = {Constants.FIRE_STONE, (byte) (Pokemon.FLAREON.ordinal() + 1)};
		writeToRom(ch, Constants.EVOS_ATTACKS + Constants.EVOS_ATTACKS_LENGTH * Pokemon.EEVEE.ordinal() + 1, bytes);
	}

	static void setEeveeEspeon (FileChannel ch) throws IOException {

		// EVOLVE_ITEM, WATER_STONE, VAPOREON -> EVOLVE_HAPPINESS, TR_MORNDAY, ESPEON
		byte[] bytes = {Constants.EVOLVE_HAPPINESS, Constants.TR_MORNDAY, (byte) (Pokemon.ESPEON.ordinal() + 1)};
		if (Settings.replaceHappinessEvos) {
			bytes[0] = Constants.EVOLVE_LEVEL;
			bytes[1] = 30;
		}
		writeToRom(ch, Constants.EVOS_ATTACKS + Constants.EVOS_ATTACKS_LENGTH * Pokemon.EEVEE.ordinal(), bytes);
	}

	static void setEeveeUmbreon (FileChannel ch) throws IOException {

		// EVOLVE_ITEM, WATER_STONE, VAPOREON -> EVOLVE_HAPPINESS, TR_NITE, UMBREON
		byte[] bytes = {Constants.EVOLVE_HAPPINESS, Constants.TR_NITE, (byte) (Pokemon.UMBREON.ordinal() + 1)};
		if (Settings.replaceHappinessEvos) {
			bytes[0] = Constants.EVOLVE_LEVEL;
			bytes[1] = 30;
		}
		writeToRom(ch, Constants.EVOS_ATTACKS + Constants.EVOS_ATTACKS_LENGTH * Pokemon.EEVEE.ordinal(), bytes);
	}

	static void makeScytherScizorIndependent (FileChannel ch) throws IOException {

		// EVOLVE_LEVEL, 40, SCIZOR -> EVOLVE_LEVEL, 101, SCIZOR
		byte[] bytes = {(byte) 101};
		writeToRom(ch, Constants.EVOS_ATTACKS + Constants.EVOS_ATTACKS_LENGTH * Pokemon.SCYTHER.ordinal() + 1, bytes);
	}

	static void writeEvolutionChangesToRomIfAny (FileChannel ch) throws IOException {

		// apply corresponding evolution changes if any non-default evolution line was selected by the user
		if (EvoLines._SLOWPOKE.getPkmn2() == Pokemon.SLOWKING) setSlowpokeSlowing(ch);
		if (EvoLines._ODDISH.getPkmn3() == Pokemon.BELLOSSOM) setGloomBellossom(ch);
		if (EvoLines._POLIWAG.getPkmn3() == Pokemon.POLITOED) setPoliwhirlPolitoed(ch);
		if (EvoLines._TYROGUE.getPkmn2() == Pokemon.HITMONCHAN) setTyrogueHitmonchan(ch);
		if (EvoLines._TYROGUE.getPkmn2() == Pokemon.HITMONLEE) setTyrogueHitmonlee(ch);
		if (EvoLines._EEVEE.getPkmn2() == Pokemon.JOLTEON) setEeveeJolteon(ch);
		if (EvoLines._EEVEE.getPkmn2() == Pokemon.FLAREON) setEeveeFlareon(ch);
		if (EvoLines._EEVEE.getPkmn2() == Pokemon.ESPEON) setEeveeEspeon(ch);
		if (EvoLines._EEVEE.getPkmn2() == Pokemon.UMBREON) setEeveeUmbreon(ch);
	}

	static void writeModifiedParabolicGrowthRateIfSelected (FileChannel ch) throws IOException {

		if (Settings.homogenizeGrowthRates) {

			// (7/8)*x^3 - 7*x^2 + 30*x - 28
			byte[] bytes = {(byte) ((7 << 4) + 8), (byte) 0x87, 30, 28};
			writeToRom(ch, 0x50efa + 12, bytes);
		}
	}

	static void replaceHappinessEvosIfSelected (FileChannel ch) throws IOException {

		if (Settings.replaceHappinessEvos) {

			byte[] bytes = {Constants.EVOLVE_LEVEL, 36};
			writeToRom(ch, Constants.EVOS_ATTACKS + Constants.EVOS_ATTACKS_LENGTH * Pokemon.GOLBAT.ordinal(), bytes);

			bytes[1] = 40;
			writeToRom(ch, Constants.EVOS_ATTACKS + Constants.EVOS_ATTACKS_LENGTH * Pokemon.CHANSEY.ordinal(), bytes);

			bytes[1] = 14;
			writeToRom(ch, Constants.EVOS_ATTACKS + Constants.EVOS_ATTACKS_LENGTH * Pokemon.PICHU.ordinal(), bytes);
			writeToRom(ch, Constants.EVOS_ATTACKS + Constants.EVOS_ATTACKS_LENGTH * Pokemon.CLEFFA.ordinal(), bytes);
			writeToRom(ch, Constants.EVOS_ATTACKS + Constants.EVOS_ATTACKS_LENGTH * Pokemon.IGGLYBUFF.ordinal(), bytes);

			bytes[1] = 21;
			writeToRom(ch, Constants.EVOS_ATTACKS + Constants.EVOS_ATTACKS_LENGTH * Pokemon.TOGEPI.ordinal(), bytes);
		}
	}

	static void fixGlobalChecksum (FileChannel ch) throws IOException {

		ch.position(0);
		ByteBuffer rom = ByteBuffer.allocate(Constants.ROM_SIZE);
		ch.read(rom);

		rom.position(0x14e);
		byte[] old_cs = new byte[2];
		rom.get(old_cs, 0, 2);

		int checksum = 0;
		for (byte b : rom.array())
			checksum += (b) & 0xff;
		checksum -= (old_cs[0] & 0xff);
		checksum -= (old_cs[1] & 0xff);

		byte[] cs = new byte[2];
		cs[0] = (byte) ((checksum >> 8) & 0xff);
		cs[1] = (byte) (checksum & 0xff);

		writeToRom(ch, 0x14e, cs);
	}

	static void print (String str) {

		JOptionPane.showMessageDialog(null, str);
	}

}
