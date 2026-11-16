package com.xiangqi.core;

import com.xiangqi.core.engine.SearchEngine;
import com.xiangqi.core.engine.SearchResult;
import com.xiangqi.core.piece.Piece;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChessRulesTest {

    private static Position p(int row, int col) {
        return new Position(row, col);
    }

    private static Move m(int fromRow, int fromCol, int toRow, int toCol) {
        return new Move(p(fromRow, fromCol), p(toRow, toCol));
    }

    @Test
    void initialPositionHas32PiecesAnd44MovesPerSide() {
        Board board = Board.initial();
        int count = 0;
        for (int row = 0; row < Board.ROWS; row++) {
            for (int col = 0; col < Board.COLS; col++) {
                if (board.pieceAt(row, col) != null) {
                    count++;
                }
            }
        }
        assertEquals(32, count);
        assertEquals(44, Rules.legalMoves(board, Side.RED).size());
        assertEquals(44, Rules.legalMoves(board, Side.BLACK).size());
        assertEquals(PieceType.GENERAL, board.pieceAt(0, 4).type());
        assertEquals(PieceType.GENERAL, board.pieceAt(9, 4).type());
    }

    @Test
    void horseIsBlockedByLeg() {
        Board board = Board.empty();
        board.place(Side.RED, PieceType.HORSE, 5, 5);
        board.place(Side.RED, PieceType.SOLDIER, 4, 5);

        List<Position> targets = board.pieceAt(p(5, 5)).pseudoTargets(board, p(5, 5));
        assertEquals(6, targets.size());
        assertFalse(targets.contains(p(3, 4)));
        assertFalse(targets.contains(p(3, 6)));
        assertTrue(targets.contains(p(4, 3)));
    }

    @Test
    void elephantCannotCrossRiverAndEyeCanBeBlocked() {
        Board board = Board.empty();
        board.place(Side.RED, PieceType.ELEPHANT, 5, 2);
        List<Position> targets = board.pieceAt(p(5, 2)).pseudoTargets(board, p(5, 2));
        assertEquals(Set.of(p(7, 0), p(7, 4)), new HashSet<>(targets));

        Board blocked = Board.empty();
        blocked.place(Side.RED, PieceType.ELEPHANT, 9, 2);
        blocked.place(Side.RED, PieceType.SOLDIER, 8, 1);
        List<Position> blockedTargets = blocked.pieceAt(p(9, 2)).pseudoTargets(blocked, p(9, 2));
        assertEquals(Set.of(p(7, 4)), new HashSet<>(blockedTargets));
    }

    @Test
    void cannonMustJumpExactlyOneScreenToCapture() {
        Board board = Board.empty();
        board.place(Side.RED, PieceType.CANNON, 5, 4);
        board.place(Side.RED, PieceType.SOLDIER, 3, 4);
        board.place(Side.BLACK, PieceType.CHARIOT, 1, 4);

        List<Position> targets = board.pieceAt(p(5, 4)).pseudoTargets(board, p(5, 4));
        assertTrue(targets.contains(p(4, 4)));
        assertFalse(targets.contains(p(2, 4)));
        assertTrue(targets.contains(p(1, 4)));
    }

    @Test
    void soldierOnlyMovesSidewaysAfterCrossingRiver() {
        Board board = Board.empty();
        board.place(Side.RED, PieceType.SOLDIER, 6, 4);
        assertEquals(Set.of(p(5, 4)), new HashSet<>(
                board.pieceAt(p(6, 4)).pseudoTargets(board, p(6, 4))));

        Board crossed = Board.empty();
        crossed.place(Side.RED, PieceType.SOLDIER, 4, 4);
        assertEquals(Set.of(p(3, 4), p(4, 3), p(4, 5)), new HashSet<>(
                crossed.pieceAt(p(4, 4)).pseudoTargets(crossed, p(4, 4))));
    }

    @Test
    void generalAndAdvisorStayInsidePalace() {
        Board board = Board.empty();
        board.place(Side.RED, PieceType.GENERAL, 7, 4);
        board.place(Side.RED, PieceType.ADVISOR, 9, 3);

        assertEquals(Set.of(p(8, 4), p(7, 3), p(7, 5)), new HashSet<>(
                board.pieceAt(p(7, 4)).pseudoTargets(board, p(7, 4))));
        assertEquals(Set.of(p(8, 4)), new HashSet<>(
                board.pieceAt(p(9, 3)).pseudoTargets(board, p(9, 3))));
    }

    @Test
    void blockedChariotCannotJumpOverOwnPiece() {
        Board board = Board.initial();
        assertFalse(Rules.isLegal(board, Side.RED, m(9, 0, 5, 0)));
        assertFalse(Rules.isLegal(board, Side.RED, m(9, 0, 5, 4)));
        assertTrue(Rules.isLegal(board, Side.RED, m(9, 0, 8, 0)));
    }

    @Test
    void exposingGeneralsIsIllegal() {
        Board board = Board.empty();
        board.place(Side.RED, PieceType.GENERAL, 9, 4);
        board.place(Side.BLACK, PieceType.GENERAL, 0, 4);
        board.place(Side.RED, PieceType.CHARIOT, 5, 4);

        assertFalse(Rules.isLegal(board, Side.RED, m(5, 4, 5, 3)));
        assertTrue(Rules.isLegal(board, Side.RED, m(5, 4, 4, 4)));
    }

    @Test
    void detectsCheckFromChariotHorseAndSoldier() {
        Board chariot = Board.empty();
        chariot.place(Side.BLACK, PieceType.GENERAL, 0, 4);
        chariot.place(Side.RED, PieceType.GENERAL, 9, 4);
        chariot.place(Side.RED, PieceType.SOLDIER, 5, 4);
        chariot.place(Side.RED, PieceType.CHARIOT, 0, 0);
        assertTrue(Rules.isInCheck(chariot, Side.BLACK));

        Board horse = Board.empty();
        horse.place(Side.BLACK, PieceType.GENERAL, 0, 4);
        horse.place(Side.RED, PieceType.GENERAL, 9, 4);
        horse.place(Side.RED, PieceType.SOLDIER, 5, 4);
        horse.place(Side.RED, PieceType.HORSE, 2, 3);
        assertTrue(Rules.isInCheck(horse, Side.BLACK));

        Board soldier = Board.empty();
        soldier.place(Side.BLACK, PieceType.GENERAL, 0, 4);
        soldier.place(Side.RED, PieceType.GENERAL, 9, 4);
        soldier.place(Side.RED, PieceType.SOLDIER, 1, 4);
        assertTrue(Rules.isInCheck(soldier, Side.BLACK));
    }

    @Test
    void stalematePositionHasNoMovesButNoCheck() {
        Board board = Board.empty();
        board.place(Side.BLACK, PieceType.GENERAL, 0, 4);
        board.place(Side.RED, PieceType.GENERAL, 9, 4);
        board.place(Side.RED, PieceType.HORSE, 2, 2);
        board.place(Side.RED, PieceType.HORSE, 2, 6);
        board.place(Side.RED, PieceType.SOLDIER, 5, 4);

        assertFalse(Rules.isInCheck(board, Side.BLACK));
        assertTrue(Rules.legalMoves(board, Side.BLACK).isEmpty());
    }

    @Test
    void checkmateEndsGameWithRedWin() {
        Board board = Board.empty();
        board.place(Side.BLACK, PieceType.GENERAL, 0, 4);
        board.place(Side.RED, PieceType.GENERAL, 9, 4);
        board.place(Side.RED, PieceType.CHARIOT, 1, 0);
        board.place(Side.RED, PieceType.HORSE, 2, 2);
        board.place(Side.RED, PieceType.HORSE, 2, 6);
        board.place(Side.RED, PieceType.SOLDIER, 5, 4);

        ChessGame game = ChessGame.fromPosition(board, Side.RED);
        game.play(m(1, 0, 1, 4));

        assertEquals(GameStatus.RED_WIN, game.status());
        assertEquals("将死", game.resultReason());
    }

    @Test
    void undoRestoresPreviousPosition() {
        ChessGame game = ChessGame.start();
        game.play(m(7, 7, 7, 4));
        assertEquals(1, game.plyCount());

        game.undo();
        assertEquals(0, game.plyCount());
        assertEquals(Side.RED, game.turn());
        assertEquals(GameStatus.PLAYING, game.status());
        assertNull(game.lastMove());
        assertEquals(PieceType.CANNON, game.board().pieceAt(7, 7).type());
        assertNull(game.board().pieceAt(7, 4));
    }

    @Test
    void threefoldRepetitionIsDraw() {
        ChessGame game = ChessGame.start();
        Move[] cycle = {m(9, 1, 7, 2), m(0, 1, 2, 2), m(7, 2, 9, 1), m(2, 2, 0, 1)};
        for (int round = 0; round < 2; round++) {
            for (Move move : cycle) {
                game.play(move);
            }
        }
        assertEquals(GameStatus.DRAW, game.status());
        assertEquals("三次重复局面", game.resultReason());
    }

    @Test
    void rendersTraditionalChineseNotation() {
        Board board = Board.initial();
        assertEquals("炮二平五", Notation.render(board, m(7, 7, 7, 4), Side.RED));
        assertEquals("砲8平5", Notation.render(board, m(2, 7, 2, 4), Side.BLACK));
        assertEquals("馬二进三", Notation.render(board, m(9, 7, 7, 6), Side.RED));
    }

    @Test
    void engineFindsMateInOne() {
        Board board = Board.empty();
        board.place(Side.BLACK, PieceType.GENERAL, 0, 4);
        board.place(Side.RED, PieceType.GENERAL, 9, 4);
        board.place(Side.RED, PieceType.CHARIOT, 1, 0);
        board.place(Side.RED, PieceType.HORSE, 2, 2);
        board.place(Side.RED, PieceType.HORSE, 2, 6);
        board.place(Side.RED, PieceType.SOLDIER, 5, 4);

        SearchResult result = new SearchEngine().search(board, Side.RED, 3, 2000);
        assertNotNull(result.bestMove());
        assertTrue(result.score() > 400_000);
        assertTrue(Rules.isLegal(board, Side.RED, result.bestMove()));
    }

    @Test
    void engineReturnsLegalMoveFromInitialPosition() {
        Board board = Board.initial();
        SearchResult result = new SearchEngine().search(board, Side.RED, 2, 5000);
        assertNotNull(result.bestMove());
        assertTrue(Rules.isLegal(board, Side.RED, result.bestMove()));
    }

    @Test
    void engineTimeoutLeavesBoardIntact() {
        Board board = Board.initial();
        String before = board.positionKey(Side.RED);

        // 深度远超时间预算，必然触发超时中断
        SearchResult result = new SearchEngine().search(board, Side.RED, 20, 120);

        assertEquals(before, board.positionKey(Side.RED));
        assertNotNull(result.bestMove());
        assertTrue(Rules.isLegal(board, Side.RED, result.bestMove()));
    }

    @Test
    void zobristHashTracksMakeAndUnmake() {
        Board board = Board.initial();
        long initialHash = board.zobristHash(Side.RED);
        assertEquals(Zobrist.hash(board, Side.RED), initialHash);

        Move move = m(7, 7, 7, 4);
        Piece captured = board.make(move);
        long afterMake = board.zobristHash(Side.BLACK);
        assertEquals(Zobrist.hash(board, Side.BLACK), afterMake);

        board.unmake(move, captured);
        assertEquals(initialHash, board.zobristHash(Side.RED));
        assertEquals(Zobrist.hash(board, Side.RED), board.zobristHash(Side.RED));
    }

    @Test
    void engineWinsFreeChariot() {
        Board board = Board.empty();
        board.place(Side.BLACK, PieceType.GENERAL, 0, 4);
        board.place(Side.RED, PieceType.GENERAL, 9, 4);
        board.place(Side.RED, PieceType.SOLDIER, 6, 4);
        board.place(Side.RED, PieceType.CHARIOT, 5, 0);
        board.place(Side.BLACK, PieceType.CHARIOT, 5, 8);

        SearchResult result = new SearchEngine().search(board, Side.RED, 3, 2000);

        assertEquals(m(5, 0, 5, 8), result.bestMove());
    }
}
